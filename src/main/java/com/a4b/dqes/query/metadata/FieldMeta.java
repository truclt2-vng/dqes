package com.a4b.dqes.query.metadata;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for Field (Column/Expression)
 * Maps to: dqes.qrytb_field_meta
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMeta {
    private Integer id;
    private String tenantCode;
    private String appCode;
    private String objectCode;
    private String fieldCode;
    private String fieldLabel;
    private String aliasHint;        // Hint for field alias
    private MappingType mappingType;
    
    // COLUMN mapping
    private String columnName;
    
    // EXPR mapping (sandbox)
    private String selectExprCode;      // From qrytb_expr_allowlist
    private String filterExprCode;      // Optional override
    private JsonNode exprArgs;            // JSON args
    
    // Legacy raw SQL (avoid if possible)
    private String selectExpr;
    private String filterExpr;
    private String exprLang;
    
    private String dataType;            // STRING, NUMBER, etc.
    private Boolean notNull;
    private Boolean allowSelect;
    private Boolean allowFilter;
    private Boolean allowSort;
    private Boolean defaultSelect;
    private String description;
    
    public enum MappingType {
        COLUMN,     // Physical column
        EXPR        // Computed expression
    }
    
    public boolean isColumn() {
        return mappingType == MappingType.COLUMN;
    }
    
    public boolean isExpression() {
        return mappingType == MappingType.EXPR;
    }
    
    public String getEffectiveSelectExpr() {
        if (isColumn()) {
            return columnName;
        }
        return selectExprCode != null ? selectExprCode : selectExpr;
    }
    
    public String getEffectiveFilterExpr() {
        if (isColumn()) {
            return columnName;
        }
        return filterExprCode != null ? filterExprCode : (selectExprCode != null ? selectExprCode : filterExpr);
    }
}
