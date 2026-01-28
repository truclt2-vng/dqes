/**
 * Created: Jan 28, 2026 12:21:53 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationJoinConditionRC {
    private Integer id;
    private String relationCode;
    private Integer seq;
    private String columnName;
    private String operator;
    private String valueType;
    private String valueLiteral;
    private String paramName;
    private Integer dbconnId;
}
