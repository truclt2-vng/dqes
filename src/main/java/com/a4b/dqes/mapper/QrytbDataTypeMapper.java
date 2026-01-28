package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbDataType;
import com.a4b.dqes.dto.domain.QrytbDataTypeDto;

@Mapper(config = BaseMapper.class)
public interface QrytbDataTypeMapper{
    public static final QrytbDataTypeMapper INSTANCE = Mappers.getMapper(QrytbDataTypeMapper.class);

    QrytbDataTypeDto toDto(QrytbDataType entity);

    @Mapping(target = "id", ignore = true)
    QrytbDataType clone(QrytbDataType entity);
}
