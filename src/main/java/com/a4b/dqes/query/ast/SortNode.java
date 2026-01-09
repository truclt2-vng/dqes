package com.a4b.dqes.query.ast;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an ORDER BY clause item
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortNode {
    private String objectCode;      // Which object this sort field belongs to
    private String fieldCode;       // Field code from qrytb_field_meta
    private SortDirection direction = SortDirection.ASC;
    private NullsOrder nullsOrder = NullsOrder.LAST;
    
    // For computed expressions
    private String exprCode;        // From qrytb_expr_allowlist (optional)
    private Object exprArgs;        // Arguments for template substitution
    
    public SortNode(String objectCode, String fieldCode, SortDirection direction) {
        this.objectCode = objectCode;
        this.fieldCode = fieldCode;
        this.direction = direction;
    }
    
    public boolean isExpression() {
        return exprCode != null;
    }
    
    public enum SortDirection {
        ASC, DESC
    }
    
    public enum NullsOrder {
        FIRST, LAST
    }
}
