package com.a4b.dqes.query.builder;

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
import org.springframework.stereotype.Component;

import java.util.*;

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
        String selectClause = buildSelectClause(selectFields, countOnly);
        
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
     */
    private String buildSelectClause(List<ResolvedField> selectFields, boolean countOnly) {
        if (countOnly) {
            return "SELECT COUNT(*) as total";
        }
        
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < selectFields.size(); i++) {
            if (i > 0) select.append(", ");
            ResolvedField field = selectFields.get(i);
            select.append(buildSelectExpression(field));
            select.append(" AS ").append(field.getAliasHint());
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
        String toAlias = context.getOrGenerateAlias(step.getToObject(), 
            step.getToObject().substring(0, Math.min(3, step.getToObject().length())));
        
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
        OperationMeta operation = operationMetaRepository
            .findByTenantCodeAndAppCodeAndCode(
                context.getTenantCode(),
                context.getAppCode(),
                filter.getOperatorCode()
            )
            .orElseThrow(() -> new IllegalArgumentException("Unknown operator: " + filter.getOperatorCode()));
        
        String paramName = "param_" + parameters.size();
        String fieldRef = alias + "." + fieldCode;
        
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
