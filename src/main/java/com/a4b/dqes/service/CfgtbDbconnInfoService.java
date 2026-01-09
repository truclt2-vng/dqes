package com.a4b.dqes.service;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkApproveCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkDeleteCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.CreateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.UpdateCfgtbDbconnInfoCmd;
import com.a4b.dqes.base.BaseAggService;
import com.a4b.dqes.dto.domain.CfgtbDbconnInfoDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CfgtbDbconnInfoService extends BaseAggService<CfgtbDbconnInfoDto, CreateCfgtbDbconnInfoCmd, UpdateCfgtbDbconnInfoCmd, BulkApproveCfgtbDbconnInfoCmd, BulkDeleteCfgtbDbconnInfoCmd> {
    
    protected CfgtbDbconnInfoService(CommandGateway commandGateway) {
        super(commandGateway);
        selectSql = "SELECT t.* ";
        fromSql = " FROM {h-schema}cfgtb_dbconn_info t";
        selectCountSql = "SELECT count(t.id) ";
    }

    @Override
    protected String permissionCheckExp() {
        return null;
    }
}
