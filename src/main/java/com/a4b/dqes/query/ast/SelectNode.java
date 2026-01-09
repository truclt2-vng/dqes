package com.a4b.dqes.query.ast;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a field in SELECT clause
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectNode {
    private String objectCode;      // Which object this field belongs to
    private String fieldCode;       // Field code from qrytb_field_meta
    private String alias;           // Optional alias for result
    
    // For computed expressions (EXPR mapping type)
    private String exprCode;        // From qrytb_expr_allowlist
    private Object exprArgs;        // Arguments for template substitution
    
    public SelectNode(String objectCode, String fieldCode) {
        this.objectCode = objectCode;
        this.fieldCode = fieldCode;
    }
    
    public SelectNode(String objectCode, String fieldCode, String alias) {
        this.objectCode = objectCode;
        this.fieldCode = fieldCode;
        this.alias = alias;
    }
    
    public boolean isExpression() {
        return exprCode != null;
    }
}
