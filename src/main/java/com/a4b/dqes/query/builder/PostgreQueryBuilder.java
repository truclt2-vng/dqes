/**
 * Created: Jan 26, 2026 12:18:53 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.a4b.dqes.dto.schemacache.FieldMetaRC;
import com.a4b.dqes.dto.schemacache.ObjectMetaRC;
import com.a4b.dqes.dto.schemacache.RelationJoinConditionRC;
import com.a4b.dqes.dto.schemacache.RelationJoinKeyRC;
import com.a4b.dqes.query.builder.filter.FilterSpec;
import com.a4b.dqes.query.builder.filter.PostgreFilterBuilder;
import com.a4b.dqes.query.dto.FilterCriteria;
import com.a4b.dqes.query.dto.SortCriteria;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.planner.FieldKey;
import com.a4b.dqes.query.planner.PlanRequest;
import com.a4b.dqes.query.planner.Planner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostgreQueryBuilder {

    public SqlQuery buildQuery(QueryContext context, 
        Planner planner,
        PlanRequest planRequest,
        List<FilterCriteria> filters,
         Map<String, SortCriteria> sorts) 
    {
        SqlQuery query = new SqlQuery();
        Map<String, Object> parameters = new HashMap<>();

        String select = context.isCountOnly() ? buildSelectCount() : buildSelectFields(context, planRequest, planner, context.isDistinct());
        String from = buildFromClause(context, planner);
        String where = buildWhereClause(parameters, filters, context, 0);
        StringBuilder sql = new StringBuilder(select).append(from);

        if(where != null && !where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        }

        // Add GROUP BY clause if there are ONE_TO_MANY relations
        String groupBy = buildGroupByClause(context, planRequest, planner);
        if (groupBy != null && !groupBy.isEmpty()) {
            sql.append(groupBy);
        }

        String orderBy = buildSort(context, sorts);
        if (orderBy != null) {
            sql.append(orderBy);
        }

        if (!context.isCountOnly()) {
            if (context.getLimit() != null) {
                sql.append(" LIMIT :limit");
                parameters.put("limit", context.getLimit());
            }
            if (context.getOffset() != null && context.getOffset() > 0) {
                sql.append(" OFFSET :offset");
                parameters.put("offset", context.getOffset());
            }
        }

        query.setSql(sql.toString());
        query.setParameters(parameters);
        return query;
    }

    private String buildSelectCount() {
        return "SELECT  COUNT(*) AS total ";
    }

    private String buildSelectFields(QueryContext context, PlanRequest planRequest, Planner planner, boolean distinct) {
        StringBuilder selectClause = new StringBuilder("SELECT " + (distinct ? "DISTINCT " : ""));
        boolean first = true;

        Map<String, List<String>> resolvedSelectField = resolvedSelectField(planRequest.getSelectFields());

        for (Map.Entry<String, List<String>> entry : resolvedSelectField.entrySet()) {
            String objectCode = entry.getKey(); // objectCode
            List<String> fieldCodes = entry.getValue(); // list of fieldCodes
            ObjectMetaRC objectMeta = context.getObjectMetaPlan().get(objectCode);
            boolean isRootObject = planRequest.getRootObject().getObjectCode().equals(objectCode);
            String runtimeAlias = context.getObjectAliases().get(objectCode);
            
            // Check if this is a ONE_TO_MANY relation
            boolean isOneToMany = isOneToManyRelation(planner, objectCode);
            
            if(isRootObject) {
                for (String fieldCode : fieldCodes) {
                    if (!first) selectClause.append(", ");
                    first = false;
                    FieldMetaRC fieldMeta = getFieldMeta(objectMeta, fieldCode);
                    selectClause.append(buildSelectExpression(runtimeAlias, fieldMeta.getColumnName()));
                    selectClause.append(" AS ").append(fieldMeta.getAliasHint());
                }
                
            } else if (isOneToMany) {
                // For ONE_TO_MANY, use COALESCE with jsonb_agg and FILTER
                if (!first) selectClause.append(", ");
                first = false;
                
                // Find a primary key or unique field to filter null rows
                String pkField = "id";
                // String pkField = objectMeta.getPrimaryKey();
                // FieldMeta pkField = objectMeta.getFieldMetas().stream()
                //     .filter(fm -> Boolean.TRUE.equals(fm.getPrimaryKey()))
                //     .findFirst()
                //     .orElse(objectMeta.getFieldMetas().get(0)); // fallback to first field
                
                selectClause.append("COALESCE(jsonb_agg(jsonb_build_object(");
                for (int i = 0; i < fieldCodes.size(); i++) {
                    String fieldCode = fieldCodes.get(i);
                    if (i > 0) selectClause.append(", ");
                    FieldMetaRC fieldMeta = getFieldMeta(objectMeta, fieldCode);
                    selectClause.append("'").append(fieldMeta.getAliasHint()).append("', ");
                    selectClause.append(buildSelectExpression(runtimeAlias, fieldMeta.getColumnName()));
                }
                selectClause.append(")) FILTER (WHERE ")
                    .append(runtimeAlias).append(".").append(pkField)
                    .append(" IS NOT NULL), '[]'::jsonb) AS ").append(objectCode);
                
            } else {
                // For ONE_TO_ONE, MANY_TO_ONE, build jsonb_build_object
                if (!first) selectClause.append(", ");
                first = false;

                selectClause.append("jsonb_build_object(");
                for (int i = 0; i < fieldCodes.size(); i++) {
                    String fieldCode = fieldCodes.get(i);
                    if (i > 0) selectClause.append(", ");
                    FieldMetaRC fieldMeta = getFieldMeta(objectMeta, fieldCode);
                    selectClause.append("'").append(fieldMeta.getAliasHint()).append("', ");
                    selectClause.append(buildSelectExpression(runtimeAlias, fieldMeta.getColumnName()));
                }
                
                selectClause.append(")");
                selectClause.append(" AS ").append(objectCode);
            }
        }
        
        return selectClause.toString();
    }

    private String buildFromClause(QueryContext context, Planner planner) {
        StringBuilder fromClause = new StringBuilder(" FROM ");
        String rootAlias = context.getObjectAliases().get(planner.getObjectCode());
        fromClause.append(context.getRootTable()).append(" ").append(rootAlias).append(" ");

        if(planner.getJoinSteps() == null){
            return fromClause.toString();
        }
        planner.getJoinSteps().forEach(step -> {
            String joinType = step.getRelationInfo().getJoinType();
            String runtimeFromAlias = context.getObjectAliases().get(step.getFromObjectCode());
            if(runtimeFromAlias == null){
                runtimeFromAlias = context.getObjectAliases().get(step.getFromAlias());
            }
            String runtimeToAlias = context.getObjectAliases().get(step.getToObjectCode());
            List<RelationJoinKeyRC> joinKeys = step.getRelationInfo().getJoinKeys();
            List<RelationJoinConditionRC> joinConditions = step.getRelationInfo().getJoinConditions();
            fromClause.append(" ").append(joinType).append(" ")
                .append(step.getJoinTable()).append(" ").append(runtimeToAlias)
                .append(" ON ");
            
            //build join conditions
            for (int i = 0; i < joinKeys.size(); i++) {
                if (i > 0) fromClause.append(" AND ");
                RelationJoinKeyRC key = joinKeys.get(i);
                
                fromClause.append(runtimeFromAlias).append(".").append(key.getFromColumnName());
                
                // Handle null-safe comparison if specified
                if (Boolean.TRUE.equals(key.getNullSafe())) {
                    fromClause.append(" IS NOT DISTINCT FROM ");
                } else {
                    fromClause.append(" ").append(key.getOperator()).append(" ");
                }
                
                fromClause.append(runtimeToAlias).append(".").append(key.getToColumnName());
            }
            // Append additional join conditions if any
            for (RelationJoinConditionRC condition : joinConditions) {
                fromClause.append(" AND ");
                fromClause.append(runtimeToAlias).append(".").append(condition.getColumnName())
                    .append(" ").append(condition.getOperator()).append(" ");
                
                // Handle different value types
                if ("CONST".equals(condition.getValueType())) {
                    fromClause.append(condition.getValueLiteral());
                } else if ("PARAM".equals(condition.getValueType())) {
                    fromClause.append(":").append(condition.getParamName());
                } else if ("EXPR".equals(condition.getValueType())) {
                    fromClause.append(condition.getValueLiteral());
                } else {
                    throw new IllegalArgumentException("Unsupported value type in join condition: " + condition.getValueType());
                }
            }
                
        });

        return fromClause.toString();
    }

    /**
     * Build WHERE clause with support for all operators including EXISTS/NOT EXISTS
     * Uses NamedParameterJdbcTemplate safe parameter binding
     */
    private String buildWhereClause(
        Map<String, Object> parameters,
        List<FilterCriteria> filters,
        QueryContext context,
        int level
    ) {
        StringBuilder whereClause = new StringBuilder();
        if(filters == null || filters.isEmpty()) {
            return null;
        }
        for (int i = 0; i < filters.size(); i++) {
            if (i > 0) {
                String logicalOp = filters.get(i).getLogicalOperator();
                whereClause.append(" ").append(logicalOp != null ? logicalOp : "AND").append(" ");
            }
            
            FilterCriteria filter = filters.get(i);
            
            if (filter.getSubFilters() != null && !filter.getSubFilters().isEmpty()) {
                whereClause.append("(");
                whereClause.append(buildWhereClause(parameters, filter.getSubFilters(), context, level + 1));
                whereClause.append(")");
            } else if (filter.getField() != null) {
                buildFilterCondition(whereClause, parameters, filter, context);
            }
        }
        return whereClause.toString();
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

        ObjectMetaRC objectMeta = context.getObjectMetaPlan().get(objectCode);
        
        FieldMetaRC fieldMeta = getFieldMeta(objectMeta, fieldCode);
        String fieldColumnName = fieldMeta.getColumnName();
        String fieldRef = alias + "." + fieldColumnName;
        String dataType = fieldMeta.getDataType();

        FilterSpec filterSpec = new FilterSpec(
            fieldRef,
            filter.getOperator().toUpperCase(),
            dataType,
            filter.getValue(),
            filter.getValue2()
        );
        
        String condition = PostgreFilterBuilder.getFilter(parameters, filterSpec);
        sql.append(fieldRef).append(condition);
    }

    private String buildSort(QueryContext context, Map<String, SortCriteria> sorts){
        StringBuilder sortClause = new StringBuilder();
        if(sorts == null || sorts.isEmpty()) {
            return null;
        }
        sortClause.append(" ORDER BY ");
        int i = 0;
        for (Map.Entry<String, SortCriteria> entry : sorts.entrySet()) {
            String sortField = entry.getKey();
            SortCriteria sort = entry.getValue();
            if (i > 0) {
                sortClause.append(", ");
            }
            String[] parts = sortField.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid field path: " + sortField);
            }
            String objectCode = parts[0];
            String fieldCode = parts[1];
            String alias = context.getObjectAliases().get(objectCode);
            if (alias == null) {
                throw new IllegalStateException("Object alias not found for: " + objectCode);
            }
            ObjectMetaRC objectMeta = context.getObjectMetaPlan().get(objectCode);
            FieldMetaRC fieldMeta = getFieldMeta(objectMeta, fieldCode);
            String fieldColumnName = fieldMeta.getColumnName();
            sortClause.append(alias).append(".").append(fieldColumnName)
                .append(" ").append(sort.getDir().toUpperCase());
            
            // Handle nulls ordering
            if (sort.getNulls() != null && !sort.getNulls().isEmpty()) {
                String nullsHandling = sort.getNulls().toUpperCase();
                if ("first".equals(nullsHandling)) {
                    sortClause.append(" NULLS FIRST");
                } else if ("last".equals(nullsHandling)) {
                    sortClause.append(" NULLS LAST");
                }
            }
            i++;
        }
        return sortClause.toString();
    }

    private FieldMetaRC getFieldMeta(ObjectMetaRC objectMeta, String fieldCode) {
        return objectMeta.getFieldMetas().stream()
            .filter(fm -> fm.getFieldCode().equals(fieldCode))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("FieldMeta not found for fieldCode: " + fieldCode + " in object: " + objectMeta.getObjectCode()));
    }

    private Map<String, List<String>> resolvedSelectField(List<FieldKey> selectFields) {
        Map<String, List<String>> resolved = new HashMap<>();
        for (FieldKey fieldKey : selectFields) {
            resolved.computeIfAbsent(fieldKey.objectCode(), k -> new java.util.ArrayList<>())
                .add(fieldKey.fieldCode());
        }
        return resolved;
    }

    private String buildSelectExpression(String alias, String columnName) {
        return alias + "." + columnName;
    }

    /**
     * Check if the given object code is part of a ONE_TO_MANY relation
     */
    private boolean isOneToManyRelation(Planner planner, String objectCode) {
        if (planner.getJoinSteps() == null) {
            return false;
        }
        return planner.getJoinSteps().stream()
            .anyMatch(step -> step.getToObjectCode().equals(objectCode) 
                && "ONE_TO_MANY".equals(step.getRelationType()));
    }

    /**
     * Check if planner contains any ONE_TO_MANY relations
     */
    private boolean hasOneToManyRelation(Planner planner) {
        if (planner.getJoinSteps() == null) {
            return false;
        }
        return planner.getJoinSteps().stream()
            .anyMatch(step -> "ONE_TO_MANY".equals(step.getRelationType()));
    }

    /**
     * Build GROUP BY clause for queries with ONE_TO_MANY relations
     * Groups by all root object fields and non-aggregated related object fields
     */
    private String buildGroupByClause(QueryContext context, PlanRequest planRequest, Planner planner) {
        if (!hasOneToManyRelation(planner)) {
            return null;
        }

        StringBuilder groupByClause = new StringBuilder(" GROUP BY ");
        boolean first = true;

        Map<String, List<String>> resolvedSelectField = resolvedSelectField(planRequest.getSelectFields());

        for (Map.Entry<String, List<String>> entry : resolvedSelectField.entrySet()) {
            String objectCode = entry.getKey();
            List<String> fieldCodes = entry.getValue();
            
            // Skip ONE_TO_MANY relations in GROUP BY (they're aggregated)
            if (isOneToManyRelation(planner, objectCode)) {
                continue;
            }

            ObjectMetaRC objectMeta = context.getObjectMetaPlan().get(objectCode);
            String runtimeAlias = context.getObjectAliases().get(objectCode);
            
            for (String fieldCode : fieldCodes) {
                if (!first) groupByClause.append(", ");
                first = false;
                
                FieldMetaRC fieldMeta = getFieldMeta(objectMeta, fieldCode);
                groupByClause.append(runtimeAlias).append(".").append(fieldMeta.getColumnName());
            }
        }

        return groupByClause.toString();
    }
}
