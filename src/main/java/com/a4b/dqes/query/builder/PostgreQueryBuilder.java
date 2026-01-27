/**
 * Created: Jan 26, 2026 12:18:53 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationJoinKey;
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

        String select = context.isCountOnly() ? buildSelectCount() : buildSelectFields(context, planRequest, context.isDistinct());
        String from = buildFromClause(context, planner);
        String where = buildWhereClause(parameters, filters, context, 0);
        StringBuilder sql = new StringBuilder(select).append(from);

        if(where != null && !where.isEmpty()) {
            sql.append(" WHERE ").append(where);
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

    private String buildSelectFields(QueryContext context, PlanRequest planRequest, boolean distinct) {
        StringBuilder selectClause = new StringBuilder("SELECT " + (distinct ? "DISTINCT " : ""));
        boolean first = true;

        Map<String, List<String>> resolvedSelectField = resolvedSelectField(planRequest.getSelectFields());

        for (Map.Entry<String, List<String>> entry : resolvedSelectField.entrySet()) {
            String objectCode = entry.getKey(); // objectCode
            List<String> fieldCodes = entry.getValue(); // list of fieldCodes
            ObjectMeta objectMeta = context.getObjectMetaPlan().get(objectCode);
            boolean isRootObject = planRequest.getRootObject().getObjectCode().equals(objectCode);
            String runtimeAlias = context.getObjectAliases().get(objectCode);
            if(isRootObject) {
                for (String fieldCode : fieldCodes) {
                    if (!first) selectClause.append(", ");
                    first = false;
                    FieldMeta fieldMeta = objectMeta.getFieldMetas().stream()
                        .filter(fm -> fm.getFieldCode().equals(fieldCode))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("FieldMeta not found for fieldCode: " + fieldCode + " in object: " + objectCode));
                    selectClause.append(buildSelectExpression(runtimeAlias, fieldMeta.getColumnName()));
                    selectClause.append(" AS ").append(fieldMeta.getAliasHint());
                }
                
            }else{
                if (!first) selectClause.append(", ");
                first = false;

                selectClause.append("jsonb_build_object(");
                for (int i = 0; i < fieldCodes.size(); i++) {
                    String fieldCode = fieldCodes.get(i);
                    if (i > 0) selectClause.append(", ");
                    FieldMeta fieldMeta = getFieldMeta(objectMeta, fieldCode);
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
            List<RelationJoinKey> joinKeys = step.getRelationInfo().getJoinKeys();
            fromClause.append(" ").append(joinType).append(" ")
                .append(step.getJoinTable()).append(" ").append(runtimeToAlias)
                .append(" ON ");
            
            //build join conditions
            for (int i = 0; i < joinKeys.size(); i++) {
                if (i > 0) fromClause.append(" AND ");
                RelationJoinKey key = joinKeys.get(i);
                
                fromClause.append(runtimeFromAlias).append(".").append(key.getFromColumnName());
                
                // Handle null-safe comparison if specified
                if (Boolean.TRUE.equals(key.getNullSafe())) {
                    fromClause.append(" IS NOT DISTINCT FROM ");
                } else {
                    fromClause.append(" ").append(key.getOperator()).append(" ");
                }
                
                fromClause.append(runtimeToAlias).append(".").append(key.getToColumnName());
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

        ObjectMeta objectMeta = context.getObjectMetaPlan().get(objectCode);
        
        FieldMeta fieldMeta = getFieldMeta(objectMeta, fieldCode);
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
            ObjectMeta objectMeta = context.getObjectMetaPlan().get(objectCode);
            FieldMeta fieldMeta = getFieldMeta(objectMeta, fieldCode);
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

    private FieldMeta getFieldMeta(ObjectMeta objectMeta, String fieldCode) {
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
}
