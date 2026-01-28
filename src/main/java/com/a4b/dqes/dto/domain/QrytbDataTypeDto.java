package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbDataTypeDto extends ControllingFieldDto{

    private Long id;

    private String code;
    private String javaType;
    private String pgCast;
    private String description;

}
