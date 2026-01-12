package com.a4b.dqes.query.generator;

import com.a4b.dqes.query.ast.*;
import com.a4b.dqes.query.ast.JoinNode.JoinPredicate;
import com.a4b.dqes.query.ast.JoinNode.JoinStrategy;
import com.a4b.dqes.query.ast.SortNode.SortDirection;
import com.a4b.dqes.query.metadata.*;
import java.util.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

/**
 * SQL Generator for Dynamic Query Engine
 * Generates safe SQL with named parameters for NamedParameterJdbcTemplate
 * 
 * Key features:
 * - Runtime alias allocation (t0, t1, t2, ...)
 * - Named parameter binding (:param0, :param1, ...)
 * - EXISTS subquery generation for ONE_TO_MANY filter-only
 * - Expression template substitution from qrytb_expr_allowlist
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlGenerator {
    
    private final DqesMetadataRepository metadataRepo;
    
    /**
     * Generate SQL and parameter map from QueryAST
     */
    public GeneratedSql generateSql(QueryAST ast) {
        SqlContext ctx = new SqlContext(ast);
        
        // Allocate aliases
        allocateAliases(ast, ctx);
        
        // Build SQL parts
        StringBuilder sql = new StringBuilder();
        sql.append(buildSelectClause(ast, ctx));
        sql.append(buildFromClause(ast, ctx));
        sql.append(buildJoinClauses(ast, ctx));
        sql.append(buildWhereClause(ast, ctx));
        sql.append(buildOrderByClause(ast, ctx));
        sql.append(buildLimitOffsetClause(ast, ctx));
        
        GeneratedSql result = new GeneratedSql();
        result.setSql(sql.toString());
        result.setParameters(ctx.parameters);
        result.setAliasMap(ctx.aliasMap);
        
        log.debug("Generated SQL with {} parameters:\n{}", ctx.paramCounter, result.getSql());
        
        return result;
    }
    
    /**
     * Allocate runtime aliases for all objects
     * Uses aliasHint from metadata if available, otherwise generates t0, t1, t2, ...
     */
    private void allocateAliases(QueryAST ast, SqlContext ctx) {
        // Root object: use aliasHint or default to t0
        ObjectMeta rootObj = loadObjectMeta(ast, ast.getRootObject());
        String rootAlias = (rootObj.getAliasHint() != null && !rootObj.getAliasHint().isEmpty()) 
            ? rootObj.getAliasHint() 
            : "t0";
        ctx.aliasMap.put(ast.getRootObject(), rootAlias);
        ctx.aliasCounter = 1;
        
        // Allocate aliases for joined objects
        for (JoinNode join : ast.getJoins()) {
            if (!ctx.aliasMap.containsKey(join.getToObjectCode())) {
                ObjectMeta toObj = loadObjectMeta(ast, join.getToObjectCode());
                String alias;
                if (toObj.getAliasHint() != null && !toObj.getAliasHint().isEmpty()) {
                    alias = toObj.getAliasHint();
                } else {
                    alias = "t" + ctx.aliasCounter++;
                }
                ctx.aliasMap.put(join.getToObjectCode(), alias);
            }
            
            // Set aliases in JoinNode
            join.setFromAlias(ctx.aliasMap.get(join.getFromObjectCode()));
            join.setToAlias(ctx.aliasMap.get(join.getToObjectCode()));
        }
    }
    
    /**
     * Build SELECT clause
     * Groups fields by object and uses jsonb_build_object for joined objects
     */
    private String buildSelectClause(QueryAST ast, SqlContext ctx) {
        StringBuilder sb = new StringBuilder("SELECT ");
        
        // Group selects by object
        Map<String, List<SelectNode>> selectsByObject = new LinkedHashMap<>();
        for (SelectNode select : ast.getSelects()) {
            selectsByObject.computeIfAbsent(select.getObjectCode(), k -> new ArrayList<>()).add(select);
        }
        
        List<String> selectExpressions = new ArrayList<>();
        
        // Process root object fields (flat columns)
        String rootObjectCode = ast.getRootObject();
        if (selectsByObject.containsKey(rootObjectCode)) {
            for (SelectNode select : selectsByObject.get(rootObjectCode)) {
                FieldMeta field = loadFieldMeta(ast, select.getObjectCode(), select.getFieldCode());
                String alias = ctx.aliasMap.get(select.getObjectCode());
                String expr = renderFieldExpression(field, alias, select, ctx);
                
                // Priority: SelectNode.alias > FieldMeta.aliasHint > default (fieldCode only for root)
                String columnAlias;
                if (select.getAlias() != null) {
                    columnAlias = select.getAlias();
                } else if (field.getAliasHint() != null && !field.getAliasHint().isEmpty()) {
                    columnAlias = field.getAliasHint();
                } else {
                    columnAlias = select.getFieldCode();
                }
                expr += " AS " + quoteIdentifier(columnAlias);
                
                selectExpressions.add(expr);
            }
        }
        
        // Process joined object fields (as JSONB objects)
        for (Map.Entry<String, List<SelectNode>> entry : selectsByObject.entrySet()) {
            String objectCode = entry.getKey();
            
            // Skip root object (already processed)
            if (objectCode.equals(rootObjectCode)) {
                continue;
            }
            
            List<SelectNode> objectSelects = entry.getValue();
            String tableAlias = ctx.aliasMap.get(objectCode);
            ObjectMeta objectMeta = loadObjectMeta(ast, objectCode);
            
            // Build jsonb_build_object for this joined object
            StringBuilder jsonbBuilder = new StringBuilder("jsonb_build_object(");
            List<String> jsonbPairs = new ArrayList<>();
            
            for (SelectNode select : objectSelects) {
                FieldMeta field = loadFieldMeta(ast, select.getObjectCode(), select.getFieldCode());
                String expr = renderFieldExpression(field, tableAlias, select, ctx);
                
                // Determine JSON key name
                String jsonKey;
                if (select.getAlias() != null) {
                    jsonKey = select.getAlias();
                } else if (field.getAliasHint() != null && !field.getAliasHint().isEmpty()) {
                    jsonKey = field.getAliasHint();
                } else {
                    jsonKey = select.getFieldCode();
                }
                
                jsonbPairs.add("'" + jsonKey + "', " + expr);
            }
            
            jsonbBuilder.append(String.join(", ", jsonbPairs));
            jsonbBuilder.append(")");
            
            // Use object's alias hint or alias as column name
            String objectColumnAlias = (objectMeta.getAliasHint() != null && !objectMeta.getAliasHint().isEmpty())
                ? objectMeta.getAliasHint()
                : tableAlias;
            
            jsonbBuilder.append(" AS ").append(quoteIdentifier(objectColumnAlias));
            
            selectExpressions.add(jsonbBuilder.toString());
        }
        
        if (selectExpressions.isEmpty()) {
            // Default: select all fields from root object
            ObjectMeta rootObj = loadObjectMeta(ast, ast.getRootObject());
            String rootAlias = ctx.aliasMap.get(ast.getRootObject());
            selectExpressions.add(rootAlias + ".*");
        }
        
        sb.append(String.join(", ", selectExpressions));
        sb.append("\n");
        
        return sb.toString();
    }
    
    /**
     * Build FROM clause
     */
    private String buildFromClause(QueryAST ast, SqlContext ctx) {
        ObjectMeta rootObj = loadObjectMeta(ast, ast.getRootObject());
        String rootAlias = ctx.aliasMap.get(ast.getRootObject());
        
        return "FROM " + rootObj.getDbTable() + " " + rootAlias + "\n";
    }
    
    /**
     * Build JOIN clauses (or EXISTS subqueries)
     */
    private String buildJoinClauses(QueryAST ast, SqlContext ctx) {
        StringBuilder sb = new StringBuilder();
        
        for (JoinNode join : ast.getJoins()) {
            if (join.getStrategy() == JoinStrategy.JOIN) {
                sb.append(buildStandardJoin(join, ast, ctx));
            }
            // EXISTS is handled in WHERE clause
        }
        
        return sb.toString();
    }
    
    /**
     * Build standard JOIN
     */
    private String buildStandardJoin(JoinNode join, QueryAST ast, SqlContext ctx) {
        ObjectMeta toObj = loadObjectMeta(ast, join.getToObjectCode());
        String joinType = join.getJoinType() == JoinNode.JoinType.INNER ? "INNER JOIN" : "LEFT JOIN";
        
        StringBuilder sb = new StringBuilder();
        sb.append(joinType).append(" ");
        sb.append(toObj.getDbTable()).append(" ").append(join.getToAlias());
        sb.append(" ON ");
        
        List<String> onConditions = new ArrayList<>();
        for (JoinPredicate pred : join.getPredicates()) {
            String condition;
            if (pred.isNullSafe()) {
                condition = String.format("%s.%s IS NOT DISTINCT FROM %s.%s",
                    join.getFromAlias(), quoteIdentifier(pred.getFromColumn()),
                    join.getToAlias(), quoteIdentifier(pred.getToColumn())
                );
            } else {
                condition = String.format("%s.%s %s %s.%s",
                    join.getFromAlias(), quoteIdentifier(pred.getFromColumn()),
                    pred.getOperator(),
                    join.getToAlias(), quoteIdentifier(pred.getToColumn())
                );
            }
            onConditions.add(condition);
        }
        
        sb.append(String.join(" AND ", onConditions));
        sb.append("\n");
        
        return sb.toString();
    }
    
    /**
     * Build WHERE clause (includes EXISTS subqueries)
     */
    private String buildWhereClause(QueryAST ast, SqlContext ctx) {
        List<String> conditions = new ArrayList<>();
        
        // Regular filters
        for (FilterNode filter : ast.getFilters()) {
            String condition = buildFilterCondition(filter, ast, ctx);
            conditions.add(condition);
        }
        
        // EXISTS subqueries for filter-only joins
        for (JoinNode join : ast.getJoins()) {
            if (join.getStrategy() == JoinStrategy.EXISTS || 
                join.getStrategy() == JoinStrategy.EXISTS_ONLY) {
                
                // Check if this object has filters
                boolean hasFilters = ast.getFilters().stream()
                    .anyMatch(f -> f.getObjectCode().equals(join.getToObjectCode()));
                
                if (hasFilters) {
                    String existsSubquery = buildExistsSubquery(join, ast, ctx);
                    conditions.add(existsSubquery);
                }
            }
        }
        
        if (conditions.isEmpty()) {
            return "";
        }
        
        return "WHERE " + String.join(" AND ", conditions) + "\n";
    }
    
    /**
     * Build filter condition with parameter binding
     */
    private String buildFilterCondition(FilterNode filter, QueryAST ast, SqlContext ctx) {
        // Skip if this filter belongs to an EXISTS subquery
        JoinNode existsJoin = ast.getJoins().stream()
            .filter(j -> j.getToObjectCode().equals(filter.getObjectCode()))
            .filter(j -> j.getStrategy() == JoinStrategy.EXISTS || 
                        j.getStrategy() == JoinStrategy.EXISTS_ONLY)
            .findFirst()
            .orElse(null);
        
        if (existsJoin != null) {
            return null; // Will be handled in EXISTS subquery
        }
        
        FieldMeta field = loadFieldMeta(ast, filter.getObjectCode(), filter.getFieldCode());
        String alias = ctx.aliasMap.get(filter.getObjectCode());
        String fieldExpr = renderFieldExpression(field, alias, null, ctx);
        
        String opCode = filter.getOperatorCode();
        Object value = filter.getValue();
        
        return switch (opCode) {
            case "EQ" -> fieldExpr + " = " + bindParameter(ctx, value);
            case "NE" -> fieldExpr + " != " + bindParameter(ctx, value);
            case "GT" -> fieldExpr + " > " + bindParameter(ctx, value);
            case "GE" -> fieldExpr + " >= " + bindParameter(ctx, value);
            case "LT" -> fieldExpr + " < " + bindParameter(ctx, value);
            case "LE" -> fieldExpr + " <= " + bindParameter(ctx, value);
            case "IN" -> fieldExpr + " IN (" + bindParameter(ctx, value) + ")";
            case "NOT_IN" -> fieldExpr + " NOT IN (" + bindParameter(ctx, value) + ")";
            case "BETWEEN" -> {
                if (value instanceof List list && list.size() == 2) {
                    yield fieldExpr + " BETWEEN " + bindParameter(ctx, list.get(0)) + 
                          " AND " + bindParameter(ctx, list.get(1));
                }
                throw new IllegalArgumentException("BETWEEN requires array of 2 values");
            }
            case "LIKE" -> fieldExpr + " LIKE " + bindParameter(ctx, value);
            case "ILIKE" -> fieldExpr + " ILIKE " + bindParameter(ctx, value);
            case "IS_NULL" -> fieldExpr + " IS NULL";
            case "IS_NOT_NULL" -> fieldExpr + " IS NOT NULL";
            default -> throw new IllegalArgumentException("Unsupported operator: " + opCode);
        };
    }
    
    /**
     * Build EXISTS subquery for filter-only ONE_TO_MANY relations
     */
    private String buildExistsSubquery(JoinNode join, QueryAST ast, SqlContext ctx) {
        ObjectMeta toObj = loadObjectMeta(ast, join.getToObjectCode());
        String subqueryAlias = "sq_" + join.getToObjectCode().toLowerCase();
        
        StringBuilder sb = new StringBuilder();
        sb.append("EXISTS (\n");
        sb.append("  SELECT 1 FROM ").append(toObj.getDbTable()).append(" ").append(subqueryAlias).append("\n");
        sb.append("  WHERE ");
        
        // JOIN conditions
        List<String> conditions = new ArrayList<>();
        for (JoinPredicate pred : join.getPredicates()) {
            String condition = String.format("%s.%s %s %s.%s",
                join.getFromAlias(), quoteIdentifier(pred.getFromColumn()),
                pred.getOperator(),
                subqueryAlias, quoteIdentifier(pred.getToColumn())
            );
            conditions.add(condition);
        }
        
        // Filters on the joined object
        for (FilterNode filter : ast.getFilters()) {
            if (filter.getObjectCode().equals(join.getToObjectCode())) {
                FieldMeta field = loadFieldMeta(ast, filter.getObjectCode(), filter.getFieldCode());
                String fieldExpr = renderFieldExpression(field, subqueryAlias, null, ctx);
                
                String filterCondition = buildFilterConditionForSubquery(
                    fieldExpr, filter.getOperatorCode(), filter.getValue(), ctx
                );
                conditions.add(filterCondition);
            }
        }
        
        sb.append(String.join(" AND ", conditions));
        sb.append("\n)");
        
        return sb.toString();
    }
    
    private String buildFilterConditionForSubquery(String fieldExpr, String opCode, 
                                                    Object value, SqlContext ctx) {
        return switch (opCode) {
            case "EQ" -> fieldExpr + " = " + bindParameter(ctx, value);
            case "NE" -> fieldExpr + " != " + bindParameter(ctx, value);
            case "GT" -> fieldExpr + " > " + bindParameter(ctx, value);
            case "GE" -> fieldExpr + " >= " + bindParameter(ctx, value);
            case "LT" -> fieldExpr + " < " + bindParameter(ctx, value);
            case "LE" -> fieldExpr + " <= " + bindParameter(ctx, value);
            case "IN" -> fieldExpr + " IN (" + bindParameter(ctx, value) + ")";
            case "NOT_IN" -> fieldExpr + " NOT IN (" + bindParameter(ctx, value) + ")";
            case "IS_NULL" -> fieldExpr + " IS NULL";
            case "IS_NOT_NULL" -> fieldExpr + " IS NOT NULL";
            default -> throw new IllegalArgumentException("Unsupported operator in subquery: " + opCode);
        };
    }
    
    /**
     * Build ORDER BY clause
     */
    private String buildOrderByClause(QueryAST ast, SqlContext ctx) {
        if (ast.getSorts().isEmpty()) {
            return "";
        }
        
        List<String> orderItems = new ArrayList<>();
        
        for (SortNode sort : ast.getSorts()) {
            FieldMeta field = loadFieldMeta(ast, sort.getObjectCode(), sort.getFieldCode());
            String alias = ctx.aliasMap.get(sort.getObjectCode());
            String expr = renderFieldExpression(field, alias, null, ctx);
            
            String direction = sort.getDirection() == SortDirection.ASC ? "ASC" : "DESC";
            String nullsOrder = sort.getNullsOrder() == SortNode.NullsOrder.FIRST ? "NULLS FIRST" : "NULLS LAST";
            
            orderItems.add(expr + " " + direction + " " + nullsOrder);
        }
        
        return "ORDER BY " + String.join(", ", orderItems) + "\n";
    }
    
    /**
     * Build LIMIT/OFFSET clause
     */
    private String buildLimitOffsetClause(QueryAST ast, SqlContext ctx) {
        StringBuilder sb = new StringBuilder();
        
         ast.getLimit();
        if (ast.getLimit() != null) {
            sb.append("LIMIT ").append(ast.getLimit()).append("\n");
        }
        
        if (ast.getOffset() != null) {
            sb.append("OFFSET ").append(ast.getOffset()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Render field expression (column or computed)
     */
    private String renderFieldExpression(FieldMeta field, String tableAlias, 
                                         SelectNode select, SqlContext ctx) {
        if (field.isColumn()) {
            return tableAlias + "." + quoteIdentifier(field.getColumnName());
        }
        
        // Expression field
        String exprCode = field.getSelectExprCode();
        if (exprCode == null && field.getSelectExpr() != null) {
            // Legacy raw SQL (not recommended)
            log.warn("Using legacy raw SQL expression for field: {}.{}", 
                field.getObjectCode(), field.getFieldCode());
            return field.getSelectExpr().replace("{alias}", tableAlias);
        }
        
        if (exprCode != null) {
            // Use safe expression template
            ExprAllowlist exprMeta = metadataRepo.findExprAllowlist(
                field.getTenantCode(), field.getAppCode(), exprCode
            ).orElseThrow(() -> new IllegalStateException(
                "Expression template not found: " + exprCode
            ));
            
            String template = exprMeta.getSqlTemplate();
            
            // Substitute placeholders {0}, {1}, ... with args
            // For now, simple substitution - enhance with actual arg resolution
            String rendered = template.replace("{0}", tableAlias + "." + quoteIdentifier(field.getColumnName()));
            
            return rendered;
        }
        
        throw new IllegalStateException("No expression defined for field: " + field.getFieldCode());
    }
    
    /**
     * Bind parameter and return placeholder
     */
    private String bindParameter(SqlContext ctx, Object value) {
        String paramName = "param" + ctx.paramCounter++;
        ctx.parameters.addValue(paramName, value);
        return ":" + paramName;
    }
    
    /**
     * Quote identifier if needed
     */
    private String quoteIdentifier(String identifier) {
        // Simple implementation - enhance with keyword checking
        if (identifier.contains(" ") || identifier.contains("-")) {
            return "\"" + identifier + "\"";
        }
        return identifier;
    }
    
    /**
     * Load ObjectMeta with error handling
     */
    private ObjectMeta loadObjectMeta(QueryAST ast, String objectCode) {
        return metadataRepo.findObjectMeta(ast.getTenantCode(), ast.getAppCode(), objectCode)
            .orElseThrow(() -> new IllegalArgumentException(
                "Object metadata not found: " + objectCode
            ));
    }
    
    /**
     * Load FieldMeta with error handling
     */
    private FieldMeta loadFieldMeta(QueryAST ast, String objectCode, String fieldCode) {
        return metadataRepo.findFieldMeta(ast.getTenantCode(), ast.getAppCode(), objectCode, fieldCode)
            .orElseThrow(() -> new IllegalArgumentException(
                "Field metadata not found: " + objectCode + "." + fieldCode
            ));
    }
    
    /**
     * SQL generation context
     */
    private static class SqlContext {
        final QueryAST ast;
        final Map<String, String> aliasMap = new HashMap<>();
        final MapSqlParameterSource parameters = new MapSqlParameterSource();
        int aliasCounter = 0;
        int paramCounter = 0;
        
        SqlContext(QueryAST ast) {
            this.ast = ast;
        }
    }
    
    /**
     * Generated SQL result
     */
    @Data
    public static class GeneratedSql {
        private String sql;
        private MapSqlParameterSource parameters;
        private Map<String, String> aliasMap;
    }
}
