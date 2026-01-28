/**
 * Created: Jan 28, 2026 12:20:19 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMetaRC{
    private Integer id;
    private String objectCode;
    private String fieldCode;
    private String fieldLabel;
    private String aliasHint;
    private String mappingType;
    private String columnName;
    private String dataType;
    private Boolean notNull;
    private Boolean defaultSelect;
    private Boolean allowSelect;
    private Boolean allowFilter;
    private Boolean allowSort;
    private String description;
    private Integer dbconnId;
    private Boolean isPrimary;
}
