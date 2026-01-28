package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbExprAllowlistDto extends ControllingFieldDto{

    private Long id;

    private String exprCode;
    private String exprType;
    private String sqlTemplate;
    private Boolean allowInSelect;
    private Boolean allowInFilter;
    private Boolean allowInSort;
    private Long minArgs;
    private Long maxArgs;
    private JsonNode argsSpec;
    private String returnDataType;
    private String description;

    private QrytbDataTypeDto returnDataTypeRef;
}
