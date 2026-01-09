package com.a4b.dqes.query.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for safe expression templates
 * Maps to: dqes.qrytb_expr_allowlist
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExprAllowlist {
    private Integer id;
    private String tenantCode;
    private String appCode;
    private String exprCode;
    private ExprType exprType;
    private String sqlTemplate;         // Template with {0}, {1}, ... placeholders
    private Boolean allowInSelect;
    private Boolean allowInFilter;
    private Boolean allowInSort;
    private Integer minArgs;
    private Integer maxArgs;
    private Object argsSpec;            // JSON spec
    private String returnDataType;
    private String description;
    
    public enum ExprType {
        TEMPLATE,       // Placeholder substitution
        FUNCTION        // Direct function call
    }
}
