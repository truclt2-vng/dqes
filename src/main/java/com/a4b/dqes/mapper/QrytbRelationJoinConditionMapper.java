package com.a4b.dqes.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.base.BaseMapper;
import com.a4b.dqes.domain.QrytbRelationJoinCondition;
import com.a4b.dqes.dto.domain.QrytbRelationJoinConditionDto;

@Mapper(config = BaseMapper.class)
public interface QrytbRelationJoinConditionMapper{
    public static final QrytbRelationJoinConditionMapper INSTANCE = Mappers.getMapper(QrytbRelationJoinConditionMapper.class);

    QrytbRelationJoinConditionDto toDto(QrytbRelationJoinCondition entity);
}
