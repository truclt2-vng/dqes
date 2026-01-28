package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbExprAllowlist;
import com.a4b.dqes.dto.domain.QrytbExprAllowlistDto;

@Mapper(config = BaseMapper.class)
public interface QrytbExprAllowlistMapper{
    public static final QrytbExprAllowlistMapper INSTANCE = Mappers.getMapper(QrytbExprAllowlistMapper.class);

    @Mapping(target = "returnDataTypeRef", ignore = true)
    QrytbExprAllowlistDto toDto(QrytbExprAllowlist entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "returnDataTypeRef", ignore = true)
    QrytbExprAllowlist clone(QrytbExprAllowlist entity);
}
