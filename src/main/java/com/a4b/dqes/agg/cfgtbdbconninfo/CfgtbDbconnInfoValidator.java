package com.a4b.dqes.agg.cfgtbdbconninfo;

import java.util.List;

import com.a4b.core.server.ApplicationContextStore;
import com.a4b.core.validation.constraints.StepByStep;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.CreateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.UpdateCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkApproveCfgtbDbconnInfoCmd;
import com.a4b.dqes.agg.cfgtbdbconninfo.cmd.BulkDeleteCfgtbDbconnInfoCmd;
import com.a4b.dqes.base.BaseValidator;
import com.a4b.dqes.repository.CfgtbDbconnInfoRepository;
import com.a4b.dqes.domain.CfgtbDbconnInfo;
import com.a4b.core.server.enums.AuthStatus;
import com.a4b.core.server.hibernate_validator.CacheObject;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CfgtbDbconnInfoValidator<T> extends BaseValidator implements ConstraintValidator<StepByStep, T> {

    protected CfgtbDbconnInfoRepository repository;

    private ConstraintValidatorContext context;

    @Override
    public void initialize(StepByStep constraintAnnotation) {
        repository = ApplicationContextStore.getApplicationContext().getBean(CfgtbDbconnInfoRepository.class);
    }

    @Override
    public boolean isValid(T value, ConstraintValidatorContext context) {
        this.context = context;

        if(value instanceof CreateCfgtbDbconnInfoCmd cmd) {
            return isValid(cmd);
        } else if(value instanceof UpdateCfgtbDbconnInfoCmd cmd) {
            return isValid(cmd);
        } else if(value instanceof BulkApproveCfgtbDbconnInfoCmd cmd) {
            return isValid(cmd);
        } else if(value instanceof BulkDeleteCfgtbDbconnInfoCmd cmd) {
            return isValid(cmd);
        }

        return true;
    }

    private boolean isValid(CreateCfgtbDbconnInfoCmd cmd) {
        if(repository.existsByCode(cmd.getConnCode(), getAppCode(), getTenantCode())) {
            return duplicateViolation(context, "connCode");
        }
        return true;
    }

    private boolean isValid(UpdateCfgtbDbconnInfoCmd cmd) {
        CfgtbDbconnInfo entity = repository.findById(cmd.getId(), getAppCode(), getTenantCode());
        if(entity == null) {
            return idNotFoundViolation(context);
        }
        CacheObject.putCache(CfgtbDbconnInfoCH.CACHE_ENTITY, entity);
        return true;
    }

    private boolean isValid(BulkApproveCfgtbDbconnInfoCmd cmd) {
        List<CfgtbDbconnInfo> entities =  repository.findByIds(cmd.getIds(), getAppCode(), getTenantCode());
		if(entities == null || entities.isEmpty()){
            return idNotFoundViolation(context);
		}
		for(CfgtbDbconnInfo entity : entities) {
			if(!AuthStatus.U.name().equals(entity.getAuthStatus())) {
				return authStatusInvalidViolation(context);
			}
		}
		CacheObject.putCache(CfgtbDbconnInfoCH.CACHE_ENTITY, entities);
		return true;
    }

    private boolean isValid(BulkDeleteCfgtbDbconnInfoCmd cmd) {
        List<CfgtbDbconnInfo> entities =  repository.findByIds(cmd.getIds(), getAppCode(), getTenantCode());
		if(entities == null || entities.isEmpty()){
            return idNotFoundViolation(context);
		}
		for(CfgtbDbconnInfo entity : entities) {
			if(!AuthStatus.U.name().equals(entity.getAuthStatus())) {
				return authStatusInvalidViolation(context);
			}
		}
		CacheObject.putCache(CfgtbDbconnInfoCH.CACHE_ENTITY, entities);
		return true;
    }

}
