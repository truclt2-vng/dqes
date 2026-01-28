package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbDataTypeOp;
import com.a4b.dqes.dto.domain.QrytbDataTypeOpDto;

@Mapper(config = BaseMapper.class)
public interface QrytbDataTypeOpMapper{
    public static final QrytbDataTypeOpMapper INSTANCE = Mappers.getMapper(QrytbDataTypeOpMapper.class);

    @Mapping(target = "opCodeRef", ignore = true)
    @Mapping(target = "dataTypeCodeRef", ignore = true)
    QrytbDataTypeOpDto toDto(QrytbDataTypeOp entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "opCodeRef", ignore = true)
    @Mapping(target = "dataTypeCodeRef", ignore = true)
    QrytbDataTypeOp clone(QrytbDataTypeOp entity);
}
