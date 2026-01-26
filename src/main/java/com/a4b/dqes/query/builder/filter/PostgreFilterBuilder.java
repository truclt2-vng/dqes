/**
 * Created: Jan 26, 2026 2:55:46 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.builder.filter;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.a4b.core.ag.ag_grid.utils.QueryUtils;
import com.a4b.core.server.utils.Pair;
import com.a4b.dqes.util.DateTimeUtil;

public final class PostgreFilterBuilder {

    private static Map<String, String> dataTypeMapping = Map.of(
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

    private static final Map<String,String> operatorMap = new HashMap<>();
    public static final Map<String, List<String>> dataTypeOperator = new HashMap<>();
    static {
        operatorMap.put("EQ", " = %s");
        operatorMap.put("NE", " <> %s");
        operatorMap.put("LT", " < %s");
        operatorMap.put("LTE", " <= %s");
        operatorMap.put("LE", " <= %s");
        operatorMap.put("GT", " > %s");
        operatorMap.put("GTE", " >= %s");
        operatorMap.put("GE", " >= %s");
        operatorMap.put("BETWEEN", " BETWEEN %s AND %s");
        operatorMap.put("NOT_BETWEEN", " NOT BETWEEN %s AND %s");
        operatorMap.put("IN", " IN (%s)");
        operatorMap.put("NOT_IN", " NOT IN (%s)");
        operatorMap.put("LIKE", " LIKE %s");
        operatorMap.put("ILIKE", " ILIKE %s");
        operatorMap.put("IS_NULL", " IS NULL");
        operatorMap.put("IS_NOT_NULL", " IS NOT NULL");

        //FTS operators
        operatorMap.put("FTS", " @@ to_tsquery(%s)");
        operatorMap.put("PHFTS", " @@ phraseto_tsquery(%s)");

        // DataType Operator Mapping support 
        dataTypeOperator.put("STRING", List.of("EQ", "NE", "LIKE", "ILIKE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"));
        dataTypeOperator.put("UUID", List.of("EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"));
        dataTypeOperator.put("TSVECTOR", List.of("FTS", "PHFTS"));
        dataTypeOperator.put("DATE", List.of("EQ", "NE", "LT", "LTE", "GT", "GTE", "BETWEEN", "NOT_BETWEEN", "IS_NULL", "IS_NOT_NULL"));
        dataTypeOperator.put("TIMESTAMP", List.of("EQ", "NE", "LT", "LTE", "GT", "GTE", "BETWEEN", "NOT_BETWEEN", "IS_NULL", "IS_NOT_NULL"));
        dataTypeOperator.put("BOOLEAN", List.of("EQ", "NE", "IS_NULL", "IS_NOT_NULL"));

    }

    public static String getFilter(Map<String, Object> parameters, FilterSpec filter) {
        validateOperator(filter.dataType(), filter.operator());
        switch (filter.dataType()) {
            case "STRING":
            case "UUID":
            case "BOOLEAN":
                return PostgreFilterBuilder.genericFilter(parameters, filter);
            case "TSVECTOR":
                return PostgreFilterBuilder.ftsFilter(parameters, filter);
            case "DATE":
            case "TIMESTAMP":
                return PostgreFilterBuilder.datetimeFilter(parameters, filter);
            default:
                throw new IllegalArgumentException("Unsupported operator: " + filter.operator());
        }
    }

     private static String genericFilter(Map<String, Object> parameters, FilterSpec filter) {
        Pair<Object, Object> resolvedFilterValue = resolvedFilterValue(filter.dataType(), filter.value(), filter.value2());
        Object filterValue = resolvedFilterValue.getFirst();
        String paramName = "param_" + parameters.size();
        // if("LIKE".equals(filter.operator()) || "ILIKE".equals(filter.operator())) {
        //     filterValue = "%" + filterValue + "%";
        // }
        parameters.put(paramName, filterValue);
        return operatorMap.get(filter.operator()).formatted(paramPlaceHolder(paramName));
    }

    private static String ftsFilter(Map<String, Object> parameters, FilterSpec filter) {
        Pair<Object, Object> resolvedFilterValue = resolvedFilterValue(filter.dataType(), filter.value(), filter.value2());
        String filterValue = (String)resolvedFilterValue.getFirst();
        String filterValueProcessed = QueryUtils.escapePostgresLiteral(filterValue.trim());
        filterValueProcessed = QueryUtils.buildTsVectorValue(filterValueProcessed);
        String paramName = "param_" + parameters.size();
        parameters.put(paramName, filterValueProcessed);
        return operatorMap.get(filter.operator()).formatted(paramPlaceHolder(paramName));
    }

    private static String datetimeFilter(Map<String, Object> parameters, FilterSpec filter) {
        Pair<Object, Object> resolvedFilterValue = resolvedFilterValue(filter.dataType(), filter.value(), filter.value2());
        
        OffsetDateTime filterValue = (OffsetDateTime) resolvedFilterValue.getFirst();
        OffsetDateTime filterToValue = (OffsetDateTime) resolvedFilterValue.getSecond();
        String paramName = null;
        String paramName2 = null;
        switch (filter.operator()) {
            case "EQ":
                OffsetDateTime startOfDay = DateTimeUtil.startOfDay(filterValue);
                OffsetDateTime endOfDay = DateTimeUtil.endOfDay(filterValue);
                paramName = "param_" + parameters.size();
                parameters.put(paramName, startOfDay);

                paramName2 = "param_" + parameters.size();
                parameters.put(paramName2, endOfDay);
                return " BETWEEN " + paramPlaceHolder(paramName) + " AND " + paramPlaceHolder(paramName2);
            case "BETWEEN":
                paramName = "param_" + parameters.size();
                parameters.put(paramName, filterValue);

                paramName2 = "param_" + parameters.size();
                parameters.put(paramName2, filterToValue);
                return " BETWEEN " + paramPlaceHolder(paramName) + " AND " + paramPlaceHolder(paramName2);
            case "NOT_BETWEEN":
                paramName = "param_" + parameters.size();
                parameters.put(paramName, filterValue);

                paramName2 = "param_" + parameters.size();
                parameters.put(paramName2, filterToValue);
                return " NOT BETWEEN " + paramPlaceHolder(paramName) + " AND " + paramPlaceHolder(paramName2);
            default:
                throw new IllegalArgumentException("Unsupported operator: " + filter.operator());
        }
    }

    private static Pair<Object, Object> resolvedFilterValue(String dataType, Object value, Object value2) {
        String javaType = dataTypeMapping.get(dataType);
        
        Object convertedValue = convertValue(value, javaType);
        Object convertedValue2 = value2 != null ? convertValue(value2, javaType) : null;
        
        return new Pair<>(convertedValue, convertedValue2);
    }

    private static Object convertValue(Object value, String javaType) {
        if (value == null || javaType == null) {
            return value;
        }
        
        // Handle list/collection types for IN/NOT_IN operators
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> convertedList = new java.util.ArrayList<>();
            for (Object item : list) {
                convertedList.add(convertSingleValue(item, javaType));
            }
            return convertedList;
        }
        
        return convertSingleValue(value, javaType);
    }
    
    private static Object convertSingleValue(Object value, String javaType) {
        if (value == null) {
            return null;
        }
        
        // If already the correct type, return as-is
        if (value.getClass().getName().equals(javaType)) {
            return value;
        }
        
        String stringValue = value.toString();
        
        try {
            switch (javaType) {
                case "java.lang.String":
                    return stringValue;
                case "java.math.BigDecimal":
                    return new java.math.BigDecimal(stringValue);
                case "java.lang.Long":
                    return Long.parseLong(stringValue);
                case "java.lang.Boolean":
                    return Boolean.parseBoolean(stringValue);
                case "java.time.LocalDate":
                    return java.time.LocalDate.parse(stringValue);
                case "java.time.OffsetDateTime":
                    return java.time.OffsetDateTime.parse(stringValue);
                case "java.util.UUID":
                    return java.util.UUID.fromString(stringValue);
                case "com.fasterxml.jackson.databind.JsonNode":
                    // For JSON, keep as-is - requires ObjectMapper for proper conversion
                    return value;
                default:
                    return value;
            }
        } catch (Exception e) {
            return value; // Return original value if conversion fails
        }
    }

    private static String paramPlaceHolder(String param) {
        return ":" + param;
    }

    private static void validateOperator(String dataType, String operator) {
        List<String> supportedOperators = dataTypeOperator.get(dataType);
        if (supportedOperators == null || !supportedOperators.contains(operator)) {
            throw new IllegalArgumentException("Unsupported operator: " + operator + " for data type: " + dataType);
        }
    }
    
}
