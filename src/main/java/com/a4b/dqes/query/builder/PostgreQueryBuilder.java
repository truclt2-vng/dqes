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
import com.a4b.dqes.query.builder.filter.PostgreFilterBuilder;
import com.a4b.dqes.query.builder.filter.FilterSpec;
import com.a4b.dqes.query.dto.FilterCriteria;
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

    private Map<String, String> dataTypeMapping = Map.of(
        "STRING", "java.lang.String",
        "NUMBER", "java.math.BigDecimal",
        "INT", "java.lang.Long",
        "BOOLEAN", "java.lang.Boolean",
        "DATE", "java.time.LocalDate",
        "TIMESTAMP", "java.time.OffsetDateTime",
        "UUID", "java.util.UUID",
        "JSON", "com.fasterxml.jackson.databind.JsonNode",
        "TSVECTOR", "java.lang.String"
    );
    
    public SqlQuery buildQuery(QueryContext context, 
        Planner planner,
        PlanRequest planRequest,
        List<FilterCriteria> filters,
        Integer offset,
        Integer limit,
        boolean countOnly) 
    {
        SqlQuery query = new SqlQuery();
        Map<String, Object> parameters = new HashMap<>();

        String select = countOnly ? buildSelectCount() : buildSelectFields(context, planRequest);
        String from = buildFromClause(context, planner);
        String where = buildWhereClause(parameters, filters, context, 0);
        StringBuilder sql = new StringBuilder(select).append(from);

        if(where != null && !where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        }

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
        return query;
    }

    private String buildSelectCount() {
        return "SELECT  COUNT(*) AS total ";
    }

    private String buildSelectFields(QueryContext context, PlanRequest planRequest) {
        StringBuilder selectClause = new StringBuilder("SELECT ");
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


        // Build condition based on operator
        // switch (filter.getOperator()) {
        //     case "EQ":
        //         sql.append(fieldRef).append(" = :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "NE":
        //         sql.append(fieldRef).append(" != :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "GT":
        //         sql.append(fieldRef).append(" > :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "GE":
        //         sql.append(fieldRef).append(" >= :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "LT":
        //         sql.append(fieldRef).append(" < :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "LE":
        //         sql.append(fieldRef).append(" <= :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "IN":
        //         sql.append(fieldRef).append(" IN (:").append(paramName).append(")");
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "NOT_IN":
        //         sql.append(fieldRef).append(" NOT IN (:").append(paramName).append(")");
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "LIKE":
        //         sql.append(fieldRef).append(" LIKE :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "ILIKE":
        //         sql.append(fieldRef).append(" ILIKE :").append(paramName);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         break;
        //     case "IS_NULL":
        //         sql.append(fieldRef).append(" IS NULL");
        //         break;
        //     case "IS_NOT_NULL":
        //         sql.append(fieldRef).append(" IS NOT NULL");
        //         break;
        //     case "BETWEEN":
        //         String paramName2 = "param_" + (parameters.size() + 1);
        //         sql.append(fieldRef).append(" BETWEEN :").append(paramName)
        //            .append(" AND :").append(paramName2);
        //         parameters.put(paramName, resolvedFilterValue.getFirst());
        //         parameters.put(paramName2, resolvedFilterValue.getSecond());
        //         break;
        //     case "EXISTS":
        //     case "NOT_EXISTS":
        //         // EXISTS/NOT EXISTS with subquery support
        //         sql.append(filter.getOperator().equals("EXISTS") ? "EXISTS" : "NOT EXISTS");
        //         sql.append(" (SELECT 1 FROM ").append(filter.getField()).append(")");
        //         // Note: Full EXISTS/NOT EXISTS implementation would require subquery context
        //         break;
        //     default:
        //         throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
        // }
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
