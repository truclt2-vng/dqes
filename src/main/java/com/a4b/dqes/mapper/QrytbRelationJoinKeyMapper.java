package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbRelationJoinKey;
import com.a4b.dqes.dto.domain.QrytbRelationJoinKeyDto;

@Mapper(config = BaseMapper.class)
public interface QrytbRelationJoinKeyMapper{
    public static final QrytbRelationJoinKeyMapper INSTANCE = Mappers.getMapper(QrytbRelationJoinKeyMapper.class);

    @Mapping(target = "dbconn", ignore = true)
    @Mapping(target = "relation", ignore = true)
    QrytbRelationJoinKeyDto toDto(QrytbRelationJoinKey entity);
}
