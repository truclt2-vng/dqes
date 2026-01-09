package com.a4b.dqes.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.CreateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.UpdateCfgtbDbconnInfoCmd;
import com.a4b.dqes.domain.CfgtbDbconnInfo;
import com.a4b.dqes.dto.domain.CfgtbDbconnInfoDto;
import com.a4b.dqes.base.BaseMapper;

@Mapper(config = BaseMapper.class)
public interface CfgtbDbconnInfoMapper extends BaseMapper<CfgtbDbconnInfo, CfgtbDbconnInfoDto, CreateCfgtbDbconnInfoCmd, UpdateCfgtbDbconnInfoCmd> {
    public static final CfgtbDbconnInfoMapper INSTANCE = Mappers.getMapper(CfgtbDbconnInfoMapper.class);

    CfgtbDbconnInfoDto toDto(CfgtbDbconnInfo entity);

    @Mapping(target = "id", ignore = true)
    CfgtbDbconnInfo clone(CfgtbDbconnInfo entity);
}
