package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbOperationMetaDto extends ControllingFieldDto{

    private Long id;

    private String code;
    private String opSymbol;
    private String opLabel;
    private Long arity;
    private String valueShape;
    private String description;

}
