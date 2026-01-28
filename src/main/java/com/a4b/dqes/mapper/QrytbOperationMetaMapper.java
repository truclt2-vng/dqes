package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbOperationMeta;
import com.a4b.dqes.dto.domain.QrytbOperationMetaDto;

@Mapper(config = BaseMapper.class)
public interface QrytbOperationMetaMapper {
    public static final QrytbOperationMetaMapper INSTANCE = Mappers.getMapper(QrytbOperationMetaMapper.class);

    QrytbOperationMetaDto toDto(QrytbOperationMeta entity);
}
