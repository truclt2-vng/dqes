package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbDataTypeOpDto extends ControllingFieldDto{

    private Long id;

    private String dataTypeCode;
    private String opCode;

    private QrytbOperationMetaDto opCodeRef;
    private QrytbDataTypeDto dataTypeCodeRef;
}
