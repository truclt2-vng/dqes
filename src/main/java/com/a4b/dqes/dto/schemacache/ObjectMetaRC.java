/**
 * Created: Jan 28, 2026 12:20:56 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMetaRC {
    private Integer id;
    private String objectCode;
    private String objectName;
    private String dbTable;
    private String aliasHint;
    private Integer dbconnId;
    private List<FieldMetaRC> fieldMetas;
}
