package com.a4b.dqes.query;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.exception.DqesRuntimeException;
import com.a4b.dqes.query.ast.*;
import com.a4b.dqes.query.generator.SqlGenerator;
import com.a4b.dqes.query.generator.SqlGenerator.GeneratedSql;
import com.a4b.dqes.query.metadata.DqesMetadataRepository;
import com.a4b.dqes.query.metadata.FieldMeta;
import com.a4b.dqes.query.metadata.ObjectMeta;
import com.a4b.dqes.query.planner.JoinPathPlanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Dynamic Query Executor Service
 * 
 * Main entry point for executing dynamic queries:
 * 1. Build QueryAST from request
 * 2. Plan multi-hop JOINs with BFS path planner
 * 3. Generate safe SQL with NamedParameterJdbcTemplate
 * 4. Execute and return results
 * 
 * Uses dynamic DataSource based on dbconnId from request
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryExecutor {
    
    private final JoinPathPlanner joinPathPlanner;
    private final SqlGenerator sqlGenerator;
    private final DynamicDataSourceService dataSourceService;
    private final DqesMetadataRepository metadataRepo;
    
    /**
     * Execute dynamic query and return results as list of maps
     */
    public QueryResult execute(QueryRequest request) {
        log.info("Executing dynamic query: tenant={}, app={}, root={}, dbconnId={}", 
            request.getTenantCode(), request.getAppCode(), request.getRootObjectCode(), 
            request.getDbconnId());
        
        // 1. Build AST from request
        QueryAST ast = buildAST(request);
        
        // 2. Plan JOIN graph
        joinPathPlanner.planJoins(ast);
        
        // 3. Generate SQL
        GeneratedSql generatedSql = sqlGenerator.generateSql(ast);
        
        // 4. Get JDBC template for target database
        NamedParameterJdbcTemplate targetJdbc = dataSourceService.getJdbcTemplate(
            request.getTenantCode(),
            request.getAppCode(),
            request.getDbconnId()
        );
        
        // 5. Execute query on target database
        List<Map<String, Object>> rows = targetJdbc.queryForList(
            generatedSql.getSql(), 
            generatedSql.getParameters()
        );

        rows = normalizeData(rows);
        
        QueryResult result = new QueryResult();
        result.setRows(rows);
        result.setRowCount(rows.size());
        // result.setGeneratedSql(generatedSql.getSql());
        // result.setAliasMap(generatedSql.getAliasMap());
        
        log.info("Query executed successfully: {} rows returned", rows.size());
        
        return result;
    }
    
    /**
     * Execute query and return count
     */
    public long executeCount(QueryRequest request) {
        log.info("Executing count query: tenant={}, app={}, root={}, dbconnId={}", 
            request.getTenantCode(), request.getAppCode(), request.getRootObjectCode(),
            request.getDbconnId());
        
        // Build AST without SELECT clause
        QueryAST ast = buildAST(request);
        ast.getSelects().clear(); // Count doesn't need SELECT
        
        // Plan joins
        joinPathPlanner.planJoins(ast);
        
        // Generate SQL
        GeneratedSql generatedSql = sqlGenerator.generateSql(ast);
        
        // Wrap in COUNT(*)
        String countSql = "SELECT COUNT(*) FROM (\n" + 
                         generatedSql.getSql() + 
                         ") count_subquery";
        
        // Get JDBC template for target database
        NamedParameterJdbcTemplate targetJdbc = dataSourceService.getJdbcTemplate(
            request.getTenantCode(),
            request.getAppCode(),
            request.getDbconnId()
        );
        
        Long count = targetJdbc.queryForObject(
            countSql, 
            generatedSql.getParameters(), 
            Long.class
        );
        
        return count != null ? count : 0L;
    }
    
    /**
     * Build QueryAST from request
     */
    private QueryAST buildAST(QueryRequest request) {
        QueryAST ast = new QueryAST();
        ast.setTenantCode(request.getTenantCode());
        ast.setAppCode(request.getAppCode());
        ast.setDbconnId(request.getDbconnId());
        
        // Resolve root object (support both objectAlias and rootObjectCode)
        String rootObjectCode = resolveRootObjectCode(request);
        ast.setRootObject(rootObjectCode);
        
        // Build alias-to-code map for resolving field references
        Map<String, String> aliasToCodeMap = buildAliasToCodeMap(
            request.getTenantCode(), 
            request.getAppCode(), 
            request.getDbconnId()
        );
        
        // Add SELECT nodes
        if (request.getSelectFields() != null) {
            for (String fieldSpec : request.getSelectFields()) {
                // Parse alias-based format: "objectAlias.fieldAlias" or "objectAlias.fieldAlias AS customAlias"
                String field = fieldSpec;
                String customAlias = null;
                
                // Check for custom alias using " AS " separator
                if (fieldSpec.contains(" AS ") || fieldSpec.contains(" as ")) {
                    String[] parts = fieldSpec.split("\\s+[Aa][Ss]\\s+", 2);
                    field = parts[0].trim();
                    customAlias = parts.length > 1 ? parts[1].trim() : null;
                }
                
                String[] resolved = resolveField(field, null, null, 
                    aliasToCodeMap, request.getTenantCode(), request.getAppCode());
                SelectNode node = new SelectNode(
                    resolved[0],  // objectCode
                    resolved[1],  // fieldCode
                    customAlias   // optional custom alias
                );
                ast.addSelect(node);
            }
        }
        
        // Add FILTER nodes
        if (request.getFilters() != null) {
            for (QueryRequest.Filter f : request.getFilters()) {
                String[] resolved = resolveField(f.getField(), f.getObjectCode(), f.getFieldCode(),
                    aliasToCodeMap, request.getTenantCode(), request.getAppCode());
                FilterNode node = new FilterNode(
                    resolved[0],  // objectCode
                    resolved[1],  // fieldCode
                    f.getOperatorCode(),
                    f.getValue()
                );
                ast.addFilter(node);
            }
        }
        
        // Add SORT nodes
        if (request.getSorts() != null) {
            for (QueryRequest.Sort s : request.getSorts()) {
                String[] resolved = resolveField(s.getField(), s.getObjectCode(), s.getFieldCode(),
                    aliasToCodeMap, request.getTenantCode(), request.getAppCode());
                SortNode node = new SortNode(
                    resolved[0],  // objectCode
                    resolved[1],  // fieldCode
                    s.getDirection()
                );
                ast.addSort(node);
            }
        }
        
        // Pagination
        ast.setLimit(request.getLimit());
        ast.setOffset(request.getOffset());
        
        return ast;
    }
    
    /**
     * Resolve root object code from objectAlias or rootObjectCode
     */
    private String resolveRootObjectCode(QueryRequest request) {
        // Priority: rootObjectCode > objectAlias
        if (request.getRootObjectCode() != null) {
            return request.getRootObjectCode();
        }
        
        if (request.getRootObject() != null) {
            // Find object by alias hint
            List<ObjectMeta> allObjects = metadataRepo.findAllObjectMeta(
                request.getTenantCode(), 
                request.getAppCode(), 
                request.getDbconnId()
            );
            
            for (ObjectMeta obj : allObjects) {
                if (request.getRootObject().equals(obj.getAliasHint())) {
                    return obj.getObjectCode();
                }
            }
            
            throw new IllegalArgumentException(
                "Object alias not found: " + request.getRootObject()
            );
        }
        
        throw new IllegalArgumentException(
            "Either rootObjectCode or objectAlias must be specified"
        );
    }
    
    /**
     * Build map of aliasHint -> objectCode for all objects
     */
    private Map<String, String> buildAliasToCodeMap(String tenantCode, String appCode, Integer dbconnId) {
        Map<String, String> map = new HashMap<>();
        
        List<ObjectMeta> allObjects = metadataRepo.findAllObjectMeta(tenantCode, appCode, dbconnId);
        for (ObjectMeta obj : allObjects) {
            if (obj.getAliasHint() != null && !obj.getAliasHint().isEmpty()) {
                map.put(obj.getAliasHint(), obj.getObjectCode());
            }
        }
        
        return map;
    }
    
    /**
     * Resolve field from alias-based format or direct codes
     * @param field Alias-based format like "emp.emp_name"
     * @param objectCode Direct object code
     * @param fieldCode Direct field code
     * @param aliasToCodeMap Map of object aliases to codes
     * @return [objectCode, fieldCode]
     */
    private String[] resolveField(String field, String objectCode, String fieldCode,
                                   Map<String, String> aliasToCodeMap,
                                   String tenantCode, String appCode) {
        // Priority: objectCode/fieldCode > field (alias format)
        if (objectCode != null && fieldCode != null) {
            return new String[]{objectCode, fieldCode};
        }
        
        if (field != null && !field.isEmpty()) {
            // Parse "objectAlias.fieldAlias" format
            String[] parts = field.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Invalid field format: " + field + ". Expected 'objectAlias.fieldAlias'"
                );
            }
            
            String objectAlias = parts[0];
            String fieldAlias = parts[1];
            
            // Resolve object code from alias
            String resolvedObjectCode = aliasToCodeMap.get(objectAlias);
            if (resolvedObjectCode == null) {
                throw new IllegalArgumentException(
                    "Object alias not found: " + objectAlias
                );
            }
            
            // Resolve field code from alias
            String resolvedFieldCode = resolveFieldCodeFromAlias(
                tenantCode, appCode, resolvedObjectCode, fieldAlias
            );
            
            return new String[]{resolvedObjectCode, resolvedFieldCode};
        }
        
        throw new IllegalArgumentException(
            "Either objectCode/fieldCode or field (alias format) must be specified"
        );
    }
    
    /**
     * Find field code by matching alias hint
     */
    private String resolveFieldCodeFromAlias(String tenantCode, String appCode, 
                                             String objectCode, String fieldAlias) {
        List<FieldMeta> fields = metadataRepo.findFieldsByObject(tenantCode, appCode, objectCode);
        
        for (FieldMeta field : fields) {
            if (fieldAlias.equals(field.getAliasHint())) {
                return field.getFieldCode();
            }
        }
        
        throw new IllegalArgumentException(
            "Field alias not found: " + fieldAlias + " in object: " + objectCode
        );
    }

    private <T> List<T> normalizeData(List<Map<String, Object>> data) {
		if (data == null)
			return null;

		List<T> newData = new ArrayList<>(data.size());

		for (Map<String, Object> item : data) {
			Map<String, Object> dt = new HashMap<>();
			for (Entry<String, Object> entry : item.entrySet()) {
				String fieldName = entry.getKey();
				if (entry.getValue() instanceof PGobject val) {
					dt.put(fieldName, getPgObjectValue(val));
				} else {
					dt.put(fieldName, entry.getValue());
				}
			}
			newData.add((T) dt); // casting Map to T
		}

		return newData;
	}

	private Object getPgObjectValue(PGobject val) {
		if ("jsonb".equals(val.getType()) || "json".equals(val.getType())) {
			String json = val.getValue();
			if (json == null) {
				return null;
			}
			try {
				json = json.trim();
				if (json.startsWith("[")) {
					List lst = JSON.getObjectMapper().readValue(json, List.class);
					return lst;
				} else if (json.startsWith("{")) {
					return JSON.getObjectMapper().readValue(json, Map.class);
				}

				return json;
			} catch (Exception e) {
				throw new DqesRuntimeException("Failed to read PgObject value", e);
			}

		} else if ("ltree".equals(val.getType())) {
			return val.getValue();
		}

		return val;
	}
}
