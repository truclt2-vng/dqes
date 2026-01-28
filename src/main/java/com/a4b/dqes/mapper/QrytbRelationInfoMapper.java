package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbRelationInfo;
import com.a4b.dqes.dto.domain.QrytbRelationInfoDto;

@Mapper(config = BaseMapper.class)
public interface QrytbRelationInfoMapper{
    public static final QrytbRelationInfoMapper INSTANCE = Mappers.getMapper(QrytbRelationInfoMapper.class);

    @Mapping(target = "fromObjectCodeRef", ignore = true)
    @Mapping(target = "toObjectCodeRef", ignore = true)
    @Mapping(target = "dbconn", ignore = true)
    QrytbRelationInfoDto toDto(QrytbRelationInfo entity);
}
