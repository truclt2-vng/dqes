package com.a4b.dqes.web.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkApproveCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkDeleteCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.CreateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.UpdateCfgtbDbconnInfoCmd;
import com.a4b.dqes.base.BaseAggService;
import com.a4b.dqes.base.BaseAggApi;
import com.a4b.dqes.dto.domain.CfgtbDbconnInfoDto;
import com.a4b.dqes.service.CfgtbDbconnInfoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cfgtbdbconninfo")
@RequiredArgsConstructor
@Tags({
    @Tag(name = "CfgtbDbconnInfo",
        description = "API for managing CfgtbDbconnInfo entities. Provides endpoints for CRUD operations and data retrieval."
    )
})
public class CfgtbDbconnInfoApi extends BaseAggApi<
    CfgtbDbconnInfoDto, 
    CreateCfgtbDbconnInfoCmd, 
    UpdateCfgtbDbconnInfoCmd, 
    BulkApproveCfgtbDbconnInfoCmd, 
    BulkDeleteCfgtbDbconnInfoCmd> {

    private final CfgtbDbconnInfoService service;

    @Override
    protected BaseAggService<CfgtbDbconnInfoDto, CreateCfgtbDbconnInfoCmd, UpdateCfgtbDbconnInfoCmd, BulkApproveCfgtbDbconnInfoCmd, BulkDeleteCfgtbDbconnInfoCmd> getService() {
        return service;
    }
    
}
