package com.a4b.dqes.query.ast;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a filter predicate in WHERE clause
 * Supports: field OP value(s)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterNode {
    private String objectCode;      // Which object this filter applies to
    private String fieldCode;       // Field code from qrytb_field_meta
    private String operatorCode;    // From qrytb_operation_meta (EQ, IN, BETWEEN, etc.)
    
    // Value(s) - type depends on operator:
    // - SCALAR: single value
    // - ARRAY: List of values (IN, NOT_IN)
    // - RANGE: List of 2 values (BETWEEN)
    // - NONE: null (IS_NULL, IS_NOT_NULL)
    private Object value;
    
    // For computed expressions
    private String exprCode;        // From qrytb_expr_allowlist (optional)
    private Object exprArgs;        // Arguments for template substitution
    
    // Logical grouping (for future OR support)
    private String logicOperator = "AND";  // AND/OR
    
    public FilterNode(String objectCode, String fieldCode, String operatorCode, Object value) {
        this.objectCode = objectCode;
        this.fieldCode = fieldCode;
        this.operatorCode = operatorCode;
        this.value = value;
    }
    
    public boolean isExpression() {
        return exprCode != null;
    }
    
    public boolean isScalarValue() {
        return value != null && !(value instanceof List);
    }
    
    public boolean isArrayValue() {
        return value instanceof List;
    }
}
