package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbObjectMeta;
import com.a4b.dqes.dto.domain.QrytbObjectMetaDto;

@Mapper(config = BaseMapper.class)
public interface QrytbObjectMetaMapper{
    public static final QrytbObjectMetaMapper INSTANCE = Mappers.getMapper(QrytbObjectMetaMapper.class);

    @Mapping(target = "dbconn", ignore = true)
    QrytbObjectMetaDto toDto(QrytbObjectMeta entity);
}
