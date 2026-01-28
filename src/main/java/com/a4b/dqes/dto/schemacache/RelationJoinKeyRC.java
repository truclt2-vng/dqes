/**
 * Created: Jan 28, 2026 12:21:31 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationJoinKeyRC {
    private Integer id;
    private Integer relationId;
    private Integer seq;
    private String fromColumnName;
    private String operator;
    private String toColumnName;
    private Boolean nullSafe;
    private Integer dbconnId;
}