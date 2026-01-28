/**
 * Created: Jan 28, 2026 12:22:15 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationInfoRC {
    private Integer id;
    private String code;
    private String fromObjectCode;
    private String toObjectCode;
    private String relationType;
    private String joinType;
    private String joinAlias;
    private Integer dbconnId;
    private List<RelationJoinKeyRC> joinKeys;
    private List<RelationJoinConditionRC> joinConditions;
}
