package com.a4b.dqes.query.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.OperationMeta;
import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.domain.RelationJoinKey;
import com.a4b.dqes.query.dto.FilterCriteria;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.model.RelationPath;
import com.a4b.dqes.query.model.ResolvedField;
import com.a4b.dqes.repository.jpa.OperationMetaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;
import com.a4b.dqes.repository.jpa.RelationJoinKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL Builder for dynamic queries with NamedParameterJdbcTemplate
 * Supports typed operations, EXISTS, NOT EXISTS with safe parameters
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqlQueryBuilder {
    
    private final RelationInfoRepository relationInfoRepository;
    private final RelationJoinKeyRepository relationJoinKeyRepository;
    private final OperationMetaRepository operationMetaRepository;
    
    /**
     * Build complete SQL query from resolved fields and filters
     * Optimized for NamedParameterJdbcTemplate with safe parameter binding
     * Supports ONE_TO_MANY relations with JSON_AGG aggregation
     */
    public SqlQuery buildQuery(
        QueryContext context,
        List<ResolvedField> selectFields,
        List<FilterCriteria> filters,
        Integer offset,
        Integer limit,
        boolean countOnly
    ) {
        SqlQuery query = new SqlQuery();
        Map<String, Object> parameters = new HashMap<>();
        
        // Build SELECT clause
        Set<String> oneToManyObjects = detectOneToManyObjects(selectFields, context);
        String selectClause = buildSelectClause(selectFields, context.getRootObject(), countOnly, oneToManyObjects, context);
        
        // Build FROM clause with root object
        String rootAlias = context.getObjectAliases().get(context.getRootObject());
        String rootTable = context.getRootTable() != null ? context.getRootTable() : context.getObjectTable(context.getRootObject());
        StringBuilder sql = new StringBuilder();
        sql.append(selectClause)
           .append(" FROM ").append(rootTable).append(" ").append(rootAlias);
        
        // Build JOIN clauses - build all necessary joins
        buildJoins(sql, context, selectFields, filters);
        
        // Build WHERE clause
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            buildWhereClause(sql, parameters, filters, context, 0);
        }
        
        // GROUP BY clause for ONE_TO_MANY aggregation
        if (!countOnly && !oneToManyObjects.isEmpty()) {
            buildGroupByClause(sql, selectFields, context, oneToManyObjects);
        }
        
        // ORDER BY, LIMIT, OFFSET for non-count queries
        if (!countOnly) {
            if (limit != null) {
                sql.append(" LIMIT :limit");
                parameters.put("limit", limit);
            }
            if (offset != null && offset > 0) {
                sql.append(" OFFSET :offset");
                parameters.put("offset", offset);
            }
        }
        
        query.setSql(sql.toString());
        query.setParameters(parameters);
        
        if (log.isDebugEnabled()) {
            log.debug("Built SQL: {}", query.getSql());
            log.debug("Parameters: {}", parameters);
        }
        
        return query;
    }
    
    /**
     * Build SELECT clause with field expressions
     * Groups fields by object and uses jsonb_build_object for non-root objects
     * For ONE_TO_MANY relations, uses JSON_AGG to aggregate child records
     */
    private String buildSelectClause(List<ResolvedField> selectFields, String rootObject, boolean countOnly, 
                                     Set<String> oneToManyObjects, QueryContext context) {
        if (countOnly) {
            return "SELECT COUNT(*) as total";
        }
        
        // Group fields by object code
        Map<String, List<ResolvedField>> fieldsByObject = new LinkedHashMap<>();
        for (ResolvedField field : selectFields) {
            fieldsByObject.computeIfAbsent(field.getObjectCode(), k -> new ArrayList<>()).add(field);
        }
        
        StringBuilder select = new StringBuilder("SELECT ");
        boolean first = true;
        
        for (Map.Entry<String, List<ResolvedField>> entry : fieldsByObject.entrySet()) {
            String objectCode = entry.getKey();
            List<ResolvedField> fields = entry.getValue();
            
            // Check if this is the root object
            boolean isRootObject = rootObject.equals(objectCode);
            boolean isOneToMany = oneToManyObjects.contains(objectCode);
            
            if (isRootObject) {
                // For root object, select fields directly without jsonb_build_object
                for (int i = 0; i < fields.size(); i++) {
                    if (!first) select.append(", ");
                    first = false;
                    
                    ResolvedField field = fields.get(i);
                    select.append(buildSelectExpression(field));
                    select.append(" AS ").append(field.getAliasHint());
                }
            } else {
                // For non-root objects, use jsonb_build_object to create nested JSON structure
                if (!first) select.append(", ");
                first = false;
                
                String objectAlias = fields.size()>0 ? entry.getValue().get(0).getObjectAlias() : objectCode;
                
                if (isOneToMany) {
                    // For ONE_TO_MANY: use JSON_AGG with FILTER to handle NULL cases
                    String childTableAlias = fields.get(0).getRuntimeAlias();
                    String childPkColumn = getPrimaryKeyColumn(context, objectCode, childTableAlias);
                    
                    select.append("COALESCE(JSONB_AGG(");
                }
                
                select.append("jsonb_build_object(");
                for (int i = 0; i < fields.size(); i++) {
                    
                    if (i > 0) select.append(", ");
                    ResolvedField field = fields.get(i);
                    
                    // Add field name as key
                    select.append("'").append(field.getAliasHint()).append("', ");
                    
                    // Add field value expression
                    select.append(buildSelectExpression(field));
                }
                select.append(")");
                
                if (isOneToMany) {
                    // Complete JSON_AGG with FILTER and COALESCE for empty arrays
                    String childTableAlias = fields.get(0).getRuntimeAlias();
                    String childPkColumn = getPrimaryKeyColumn(context, objectCode, childTableAlias);
                    select.append(") FILTER (WHERE ").append(childPkColumn).append(" IS NOT NULL), '[]'::jsonb)");
                }
                
                select.append(" AS ").append(objectAlias);
            }
        }
        
        return select.toString();
    }
    
    private String buildSelectExpression(ResolvedField field) {
        if ("COLUMN".equals(field.getMappingType())) {
            return field.getRuntimeAlias() + "." + field.getColumnName();
        } else {
            // Handle expression-based fields (future enhancement)
            return field.getRuntimeAlias() + "." + field.getColumnName();
        }
    }
    
    /**
     * Build JOIN clauses for all required relations
     * Optimized to avoid duplicate joins and respect dependency order
     */
    private void buildJoins(
        StringBuilder sql,
        QueryContext context,
        List<ResolvedField> selectFields,
        List<FilterCriteria> filters
    ) {
        Set<String> joinedObjects = new HashSet<>();
        joinedObjects.add(context.getRootObject());
        
        // Collect all objects that need to be joined
        Set<String> requiredObjects = new LinkedHashSet<>();
        for (ResolvedField field : selectFields) {
            if (field.getRelationPath() != null) {
                for (RelationPath.PathStep step : field.getRelationPath().getSteps()) {
                    requiredObjects.add(step.getToObject());
                }
            }
        }
        
        // Add objects from filters
        if (filters != null) {
            collectObjectsFromFilters(filters, requiredObjects);
        }
        
        // Build joins in topological order using paths
        Map<String, RelationPath> processedPaths = new HashMap<>();
        for (String objectCode : requiredObjects) {
            if (joinedObjects.contains(objectCode)) {
                continue;
            }
            
            RelationPath path = context.getRelationPaths().get(objectCode);
            if (path != null && !processedPaths.containsKey(objectCode)) {
                for (RelationPath.PathStep step : path.getSteps()) {
                    String stepKey = step.getFromObject() + "->" + step.getToObject();
                    if (!joinedObjects.contains(step.getToObject())) {
                        buildJoinForStep(sql, context, step);
                        joinedObjects.add(step.getToObject());
                    }
                }
                processedPaths.put(objectCode, path);
            }
        }
    }
    
    /**
     * Build JOIN clause for a single path step
     * Uses relation metadata and join keys for safe SQL generation
     */
    private void buildJoinForStep(
        StringBuilder sql,
        QueryContext context,
        RelationPath.PathStep step
    ) {
        // Get relation metadata
        RelationInfo relation = relationInfoRepository
            .findByTenantCodeAndAppCodeAndCode(
                context.getTenantCode(),
                context.getAppCode(),
                step.getRelationCode()
            )
            .orElseThrow(() -> new IllegalArgumentException("Relation not found: " + step.getRelationCode()));
        
        // Get join keys
        List<RelationJoinKey> joinKeys = relationJoinKeyRepository
            .findByRelationIdOrderBySeq(relation.getId());
        
        if (joinKeys.isEmpty()) {
            throw new IllegalStateException("No join keys defined for relation: " + step.getRelationCode());
        }
        
        String fromAlias = context.getObjectAliases().get(step.getFromObject());
        String toAlias = context.getOrGenerateAlias(step.getToObject(),step.getToAlias());
        // String toAlias = context.getOrGenerateAlias(step.getToObject(), 
        //     step.getToObject().substring(0, Math.min(3, step.getToObject().length())));
        
        // Register table name for the to object
        String toTable = context.getObjectTable(step.getToObject());
        
        // Build JOIN clause
        sql.append(" ").append(step.getJoinType()).append(" JOIN ")
           .append(toTable).append(" ").append(toAlias)
           .append(" ON ");
        
        for (int i = 0; i < joinKeys.size(); i++) {
            if (i > 0) sql.append(" AND ");
            RelationJoinKey key = joinKeys.get(i);
            
            sql.append(fromAlias).append(".").append(key.getFromColumnName());
            
            // Handle null-safe comparison if specified
            if (Boolean.TRUE.equals(key.getNullSafe())) {
                sql.append(" IS NOT DISTINCT FROM ");
            } else {
                sql.append(" ").append(key.getOperator()).append(" ");
            }
            
            sql.append(toAlias).append(".").append(key.getToColumnName());
        }
    }
    
    /**
     * Collect all objects referenced in filters
     */
    private void collectObjectsFromFilters(
        List<FilterCriteria> filters,
        Set<String> requiredObjects
    ) {
        for (FilterCriteria filter : filters) {
            if (filter.getField() != null && filter.getField().contains(".")) {
                String objectCode = filter.getField().split("\\.")[0];
                requiredObjects.add(objectCode);
            }
            if (filter.getSubFilters() != null) {
                collectObjectsFromFilters(filter.getSubFilters(), requiredObjects);
            }
        }
    }
    
    /**
     * Build WHERE clause with support for all operators including EXISTS/NOT EXISTS
     * Uses NamedParameterJdbcTemplate safe parameter binding
     */
    private void buildWhereClause(
        StringBuilder sql,
        Map<String, Object> parameters,
        List<FilterCriteria> filters,
        QueryContext context,
        int level
    ) {
        for (int i = 0; i < filters.size(); i++) {
            if (i > 0) {
                String logicalOp = filters.get(i).getLogicalOperator();
                sql.append(" ").append(logicalOp != null ? logicalOp : "AND").append(" ");
            }
            
            FilterCriteria filter = filters.get(i);
            
            if (filter.getSubFilters() != null && !filter.getSubFilters().isEmpty()) {
                sql.append("(");
                buildWhereClause(sql, parameters, filter.getSubFilters(), context, level + 1);
                sql.append(")");
            } else if (filter.getField() != null) {
                buildFilterCondition(sql, parameters, filter, context);
            }
        }
    }
    
    /**
     * Build a single filter condition with type-safe parameter binding
     * Supports all standard operators plus EXISTS/NOT EXISTS
     */
    private void buildFilterCondition(
        StringBuilder sql,
        Map<String, Object> parameters,
        FilterCriteria filter,
        QueryContext context
    ) {
        String[] parts = filter.getField().split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid field path: " + filter.getField());
        }
        
        String objectCode = parts[0];
        String fieldCode = parts[1];
        
        String alias = context.getObjectAliases().get(objectCode);
        if (alias == null) {
            throw new IllegalStateException("Object alias not found for: " + objectCode);
        }
        
        // Get operation metadata for validation
        // OperationMeta operation = operationMetaRepository
        //     .findByTenantCodeAndAppCodeAndCode(
        //         context.getTenantCode(),
        //         context.getAppCode(),
        //         filter.getOperatorCode()
        //     )
        //     .orElseThrow(() -> new IllegalArgumentException("Unknown operator: " + filter.getOperatorCode()));
        
        String paramName = "param_" + parameters.size();
        String fieldColumnName = getFieldColumnName(context, objectCode, fieldCode);
        String fieldRef = alias + "." + fieldColumnName;
        
        // Build condition based on operator
        switch (filter.getOperatorCode()) {
            case "EQ":
                sql.append(fieldRef).append(" = :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "NE":
                sql.append(fieldRef).append(" != :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "GT":
                sql.append(fieldRef).append(" > :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "GE":
                sql.append(fieldRef).append(" >= :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "LT":
                sql.append(fieldRef).append(" < :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "LE":
                sql.append(fieldRef).append(" <= :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "IN":
                sql.append(fieldRef).append(" IN (:").append(paramName).append(")");
                parameters.put(paramName, filter.getValues());
                break;
            case "NOT_IN":
                sql.append(fieldRef).append(" NOT IN (:").append(paramName).append(")");
                parameters.put(paramName, filter.getValues());
                break;
            case "LIKE":
                sql.append(fieldRef).append(" LIKE :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "ILIKE":
                sql.append(fieldRef).append(" ILIKE :").append(paramName);
                parameters.put(paramName, filter.getValue());
                break;
            case "IS_NULL":
                sql.append(fieldRef).append(" IS NULL");
                break;
            case "IS_NOT_NULL":
                sql.append(fieldRef).append(" IS NOT NULL");
                break;
            case "BETWEEN":
                String paramName2 = "param_" + (parameters.size() + 1);
                sql.append(fieldRef).append(" BETWEEN :").append(paramName)
                   .append(" AND :").append(paramName2);
                parameters.put(paramName, filter.getValue());
                parameters.put(paramName2, filter.getValue2());
                break;
            case "EXISTS":
            case "NOT_EXISTS":
                // EXISTS/NOT EXISTS with subquery support
                sql.append(filter.getOperatorCode().equals("EXISTS") ? "EXISTS" : "NOT EXISTS");
                sql.append(" (SELECT 1 FROM ").append(filter.getField()).append(")");
                // Note: Full EXISTS/NOT EXISTS implementation would require subquery context
                break;
            default:
                throw new IllegalArgumentException("Unsupported operator: " + filter.getOperatorCode());
        }
    }

    private String getFieldColumnName(QueryContext context, String objectCode, String fieldCode) {
        ObjectMeta objectMeta = context.getAllObjectMetaMap().get(objectCode);
        return objectMeta.getFieldMetas().stream()
            .filter(f -> f.getFieldCode().equals(fieldCode))
            .findFirst()
            .map(f -> f.getColumnName())
            .orElse(fieldCode);
    }
    
    /**
     * Detect ONE_TO_MANY relations in select fields
     */
    private Set<String> detectOneToManyObjects(List<ResolvedField> selectFields, QueryContext context) {
        Set<String> oneToManyObjects = new HashSet<>();
        
        for (ResolvedField field : selectFields) {
            if (field.getRelationPath() != null && field.getRelationPath().getSteps() != null) {
                for (RelationPath.PathStep step : field.getRelationPath().getSteps()) {
                    RelationInfo relation = relationInfoRepository
                        .findByTenantCodeAndAppCodeAndCode(
                            context.getTenantCode(),
                            context.getAppCode(),
                            step.getRelationCode()
                        )
                        .orElse(null);
                    
                    if (relation != null && "ONE_TO_MANY".equals(relation.getRelationType())) {
                        oneToManyObjects.add(step.getToObject());
                    }
                }
            }
        }
        
        return oneToManyObjects;
    }
    
    /**
     * Get primary key column for an object (with alias prefix)
     */
    private String getPrimaryKeyColumn(QueryContext context, String objectCode, String tableAlias) {
        ObjectMeta objectMeta = context.getAllObjectMetaMap().get(objectCode);
        if (objectMeta != null && objectMeta.getFieldMetas() != null) {
            // Look for a field with "id" or "ID" in the name or assume first field is PK
            for (FieldMeta field : objectMeta.getFieldMetas()) {
                if (field.getFieldCode().toLowerCase().contains("id") || 
                    field.getColumnName().toLowerCase().contains("id")) {
                    return tableAlias + "." + field.getColumnName();
                }
            }
            // Fallback: use first field or "id"
            if (!objectMeta.getFieldMetas().isEmpty()) {
                return tableAlias + "." + objectMeta.getFieldMetas().get(0).getColumnName();
            }
        }
        // Last resort: assume column is "id"
        return tableAlias + ".id";
    }
    
    /**
     * Build GROUP BY clause for root object fields when ONE_TO_MANY aggregation is used
     */
    private void buildGroupByClause(StringBuilder sql, List<ResolvedField> selectFields, 
                                    QueryContext context, Set<String> oneToManyObjects) {
        String rootObject = context.getRootObject();
        String rootAlias = context.getObjectAliases().get(rootObject);
        
        // Collect all root object fields that need to be grouped
        List<String> groupByFields = new ArrayList<>();
        for (ResolvedField field : selectFields) {
            if (rootObject.equals(field.getObjectCode())) {
                // Root fields must be in GROUP BY when using aggregation
                groupByFields.add(rootAlias + "." + field.getColumnName());
            }
        }
        
        // Add non-ONE_TO_MANY related object fields to GROUP BY
        for (ResolvedField field : selectFields) {
            if (!rootObject.equals(field.getObjectCode()) && !oneToManyObjects.contains(field.getObjectCode())) {
                // These are MANY_TO_ONE or ONE_TO_ONE fields that should be grouped
                groupByFields.add(field.getRuntimeAlias() + "." + field.getColumnName());
            }
        }
        
        if (!groupByFields.isEmpty()) {
            sql.append(" GROUP BY ");
            sql.append(String.join(", ", groupByFields));
        }
    }
    
    /**
     * Inner class to hold SQL and parameters
     */
    public static class SqlQuery {
        private String sql;
        private Map<String, Object> parameters;
        
        public String getSql() {
            return sql;
        }
        
        public void setSql(String sql) {
            this.sql = sql;
        }
        
        public Map<String, Object> getParameters() {
            return parameters;
        }
        
        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }
}
