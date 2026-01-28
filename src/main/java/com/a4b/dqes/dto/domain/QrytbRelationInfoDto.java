package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbRelationInfoDto extends ControllingFieldDto{

    private Long id;

    private String code;
    private String fromObjectCode;
    private String toObjectCode;
    private String relationType;
    private String joinType;
    private String joinAlias;
    private String filterMode;
    private Boolean isRequired;
    private Boolean isNavigable;
    private Long pathWeight;
    private JsonNode relationProps;
    private String description;
    private Long dbconnId;

    private QrytbObjectMetaDto fromObjectCodeRef;
    private QrytbObjectMetaDto toObjectCodeRef;
    private CfgtbDbconnInfoDto dbconn;
}
