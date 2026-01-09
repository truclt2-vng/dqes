package com.a4b.dqes.agg.cfgtbdbconninfo;

import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import com.a4b.core.server.hibernate_validator.CacheObject;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkApproveCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.CreateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.UpdateCfgtbDbconnInfoCmd;
import com.a4b.dqes.base.BaseAggCH;
import com.a4b.dqes.base.BaseRespDto;
import com.a4b.dqes.base.dao.ComGenericDAO;
import com.a4b.dqes.crypto.CryptoService;
import com.a4b.dqes.domain.CfgtbDbconnInfo;
import com.a4b.dqes.dto.domain.CfgtbDbconnInfoDto;
import com.a4b.dqes.mapper.CfgtbDbconnInfoMapper;
import com.a4b.dqes.repository.CfgtbDbconnInfoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class CfgtbDbconnInfoCH extends BaseAggCH<CfgtbDbconnInfo, CfgtbDbconnInfoDto, CreateCfgtbDbconnInfoCmd, UpdateCfgtbDbconnInfoCmd>{

    public static final String EVENT_PREFIX = "com.a4b.dqes.event.";
    public static final String CACHE_ENTITY = "CfgtbDbconnInfo";
    private final CfgtbDbconnInfoRepository repository;
    private CfgtbDbconnInfoMapper mapper = CfgtbDbconnInfoMapper.INSTANCE;
    private final CryptoService cryptoService;

    private static final String PASSWORD_ALG = "AES_GCM";

    @Override
    protected String eventType() {
        return EVENT_PREFIX + "CfgtbDbconnInfo%sEvent";
    }

    @Override
    protected String objectType(){
        return clazz().getName();
    }

    @CommandHandler
    public BaseRespDto create(CreateCfgtbDbconnInfoCmd cmd) {
        String encryptedPassword = cryptoService.encrypt(cmd.getPasswordPlain(), PASSWORD_ALG);
        cmd.setPasswordEnc(encryptedPassword);
        cmd.setPasswordAlg(PASSWORD_ALG);
        return super.create(cmd);
    }

    @CommandHandler
    public BaseRespDto update(UpdateCfgtbDbconnInfoCmd cmd) {
        CfgtbDbconnInfo entity = CacheObject.getCache(CACHE_ENTITY);
        return super.update(entity, cmd);
    }

    @CommandHandler
    public void bulkApprove(BulkApproveCfgtbDbconnInfoCmd cmd) {
        List<CfgtbDbconnInfo> entities = CacheObject.getCache(CACHE_ENTITY);
        super.bulkApprove(entities);
    }

    @Override
    protected ComGenericDAO<CfgtbDbconnInfo, Long> repository() {
        return repository;
    }

    @Override
    protected CfgtbDbconnInfoDto toDto(CfgtbDbconnInfo entity) {
        return mapper.toDto(entity);
    }

    @Override
    protected void toEntity(CfgtbDbconnInfo entity, CreateCfgtbDbconnInfoCmd cmd) {
        mapper.toEntity(entity, cmd);
    }

    @Override
    protected void toUpdatePartialEntity(CfgtbDbconnInfo entity, UpdateCfgtbDbconnInfoCmd cmd) {
        mapper.toUpdatePartialEntity(entity, cmd);
    }

    @Override
    protected Class<CfgtbDbconnInfo> clazz() {
        return CfgtbDbconnInfo.class;
    }

    @Override
    protected String getCode(CfgtbDbconnInfo entity) {
        return entity.getConnCode();
    }
}
