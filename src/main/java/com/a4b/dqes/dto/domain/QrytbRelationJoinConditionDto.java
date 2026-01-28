package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbRelationJoinConditionDto extends ControllingFieldDto{

    private Long id;

    private String relationCode;
    private Long seq;
    private String columnName;
    private String operator;
    private String valueType;
    private String valueLiteral;
    private String paramName;
    private String description;
    private Long dbconnId;

}
