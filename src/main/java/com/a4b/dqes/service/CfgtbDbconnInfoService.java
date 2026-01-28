package com.a4b.dqes.service;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkApproveCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkDeleteCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.CreateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.UpdateCfgtbDbconnInfoCmd;
import com.a4b.dqes.base.BaseAggService;
import com.a4b.dqes.constant.CacheNames;
import com.a4b.dqes.domain.CfgtbDbconnInfo;
import com.a4b.dqes.dto.domain.CfgtbDbconnInfoDto;
import com.a4b.dqes.dto.schemacache.DbConnInfoDto;
import com.a4b.dqes.mapper.CfgtbDbconnInfoMapper;
import com.a4b.dqes.repository.jpa.CfgtbDbconnInfoJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CfgtbDbconnInfoService extends BaseAggService<CfgtbDbconnInfoDto, CreateCfgtbDbconnInfoCmd, UpdateCfgtbDbconnInfoCmd, BulkApproveCfgtbDbconnInfoCmd, BulkDeleteCfgtbDbconnInfoCmd> {
    
    private final CfgtbDbconnInfoJpaRepository cfgtbDbconnInfoJpaRepository;

    protected CfgtbDbconnInfoService(CommandGateway commandGateway, CfgtbDbconnInfoJpaRepository cfgtbDbconnInfoJpaRepository) {
        super(commandGateway);
        this.cfgtbDbconnInfoJpaRepository = cfgtbDbconnInfoJpaRepository;
        selectSql = "SELECT t.* ";
        fromSql = " FROM {h-schema}cfgtb_dbconn_info t";
        selectCountSql = "SELECT count(t.id) ";
    }
    
    // public CfgtbDbconnInfoDto findByConnCode(String connCode){
    //     CfgtbDbconnInfo data = cfgtbDbconnInfoJpaRepository.findByConnCode(connCode);
    //     return CfgtbDbconnInfoMapper.INSTANCE.toDto(data);
    // }

    // @CacheableNonNull(value = CacheNames.DB_CONN_INFO)
    @Cacheable(value = CacheNames.DB_CONN_INFO, key = "#connCode", unless = "#result == null")
    public DbConnInfoDto getDbConnInfoByCode(String connCode) {
        CfgtbDbconnInfo data = cfgtbDbconnInfoJpaRepository.findByConnCode(connCode);
        DbConnInfoDto dto = CfgtbDbconnInfoMapper.INSTANCE.toCacheDto(data);
        return dto;
    }

    @Override
    protected String permissionCheckExp() {
        return null;
    }
}
