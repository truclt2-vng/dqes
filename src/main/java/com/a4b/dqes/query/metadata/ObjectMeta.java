package com.a4b.dqes.query.metadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for Object (Table/View)
 * Maps to: dqes.qrytb_object_meta
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMeta {
    private Integer id;
    private String tenantCode;
    private String appCode;
    private String objectCode;
    private String objectName;
    private String dbTable;         // schema.table
    private String aliasHint;       // Hint only
    private Integer dbconnId;
    private String description;
    private Boolean currentFlg;
}
