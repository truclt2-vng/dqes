package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbRelationJoinKeyDto extends ControllingFieldDto{

    private Long id;

    private Long relationId;
    private Long seq;
    private String fromColumnName;
    private String operator;
    private String toColumnName;
    private Boolean nullSafe;
    private String description;
    private Long dbconnId;

    private CfgtbDbconnInfoDto dbconn;
    private QrytbRelationInfoDto relation;
}
