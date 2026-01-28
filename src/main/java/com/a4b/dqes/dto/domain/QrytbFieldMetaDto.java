package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QrytbFieldMetaDto extends ControllingFieldDto{

    private Long id;

    private String objectCode;
    private String fieldCode;
    private String fieldLabel;
    private String aliasHint;
    private String mappingType;
    private String columnName;
    private String exprLang;
    private String dataType;
    private Boolean notNull;
    private Boolean defaultSelect;
    private Boolean allowSelect;
    private Boolean allowFilter;
    private Boolean allowSort;
    private String description;
    private Long dbconnId;
    private Boolean isPrimary;

    private QrytbDataTypeDto dataTypeRef;
    private QrytbObjectMetaDto objectCodeRef;
}
