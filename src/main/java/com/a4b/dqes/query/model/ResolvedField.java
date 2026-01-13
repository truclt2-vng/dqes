package com.a4b.dqes.query.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a resolved field in the query
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedField {
    
    private String originalFieldPath; // e.g., "employee.employeeCode"
    private String objectCode;
    private String dbTable;
    private String fieldCode;
    private String columnName;
    private String dataType;
    private String aliasHint;
    private String runtimeAlias; // Generated unique alias
    private RelationPath relationPath;
    private String mappingType; // COLUMN or EXPR
    private String selectExprCode;
    private String filterExprCode;
}
