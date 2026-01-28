package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbObjectMetaDto extends ControllingFieldDto{

    private Long id;

    private String objectCode;
    private String objectName;
    private String dbTable;
    private String aliasHint;
    private Long dbconnId;
    private String description;

    private CfgtbDbconnInfoDto dbconn;
}
