package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbFieldMeta;
import com.a4b.dqes.dto.domain.QrytbFieldMetaDto;

@Mapper(config = BaseMapper.class)
public interface QrytbFieldMetaMapper {
    public static final QrytbFieldMetaMapper INSTANCE = Mappers.getMapper(QrytbFieldMetaMapper.class);

    @Mapping(target = "dataTypeRef", ignore = true)
    @Mapping(target = "objectCodeRef", ignore = true)
    QrytbFieldMetaDto toDto(QrytbFieldMeta entity);
}
