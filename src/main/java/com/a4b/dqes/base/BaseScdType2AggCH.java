/**
 * Created: Nov 05, 2025 5:26:28 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;

import java.time.OffsetDateTime;
import java.util.List;

import com.a4b.core.ag.ag_grid.utils.FullTextSearchUtil;
import com.a4b.core.server.enums.AuthStatus;
import com.a4b.dqes.base.dao.ComGenericDAO;
import com.a4b.dqes.domain.generic.BaseEntity;
import com.a4b.dqes.dto.generic.ControllingFieldDto;
import com.a4b.dqes.util.MetaDataUtils;

public abstract class BaseScdType2AggCH<
    E extends BaseEntity, 
    D extends ControllingFieldDto, 
    C, U
    > extends BaseCH {
    
    protected abstract ComGenericDAO<E, Long> repository();
    protected abstract D toDto(E entity);
    protected abstract void toEntity(E entity, C cmd);
    protected abstract void toUpdatePartialEntity(E entity, U cmd);
    protected abstract E cloneEntity(E entity);
    protected abstract String eventType();
    protected abstract Class<E> clazz();
    protected abstract E findEffectiveByCode(String code, String appCode, String tenantCode);

    protected abstract String getCode(E entity);

    @Override
    protected Boolean needApproval() {
        return false;
    }

    /* ---------------------- CREATE ---------------------- */
    public BaseRespDto create(C cmd) {
         try {
            E entity = clazz().getDeclaredConstructor().newInstance();
            toEntity(entity, cmd);
            MetaDataUtils.forCreate(entity, needApproval());
            setFTS(entity);

            resolveId(entity, cmd);

            repository().save(entity);
            repository().flush();
            D newValue = toDto(entity);
            publishEventCreate(newValue);
            return new BaseRespDto(newValue.getId(), getCode(entity), entity.getAggId());
        } catch (Exception e) {
            throw new RuntimeException("Error creating entity: " + clazz().getSimpleName(), e);
        }
        
    }

    /* ---------------------- UPDATE ---------------------- */
    public BaseRespDto update(E entity, U cmd) {
        D oldValue = toDto(entity);
        D newValue = needApproval()
                ? handleApprovalUpdate(cmd, entity)
                : handleDirectUpdate(cmd, entity);
        publishEventUpdate(oldValue, newValue);
        return new BaseRespDto(newValue.getId(), getCode(entity), entity.getAggId());
    }

    private D handleApprovalUpdate(U cmd, E entity) {
        if (AuthStatus.A.name().equals(entity.getAuthStatus())) {
            E newEntity = cloneUnauthorized(entity);
            MetaDataUtils.forUpdate(newEntity);
            toUpdatePartialEntity(newEntity, cmd);
            newEntity.setId(null);

            resolveId(newEntity, cmd);
            
            setFTS(newEntity);
            repository().save(newEntity);
            repository().flush();
            return toDto(newEntity);
        } else {
            toUpdatePartialEntity(entity, cmd);
            MetaDataUtils.forUpdate(entity);
            
            resolveId(entity, cmd);

            setFTS(entity);
            repository().save(entity);
            return toDto(entity);
        }
    }

    private D handleDirectUpdate(U cmd, E entity) {
        E newEntity = cloneEntity(entity);
        closeCurrentEntity(entity);
        MetaDataUtils.forUpdate(newEntity);
        toUpdatePartialEntity(newEntity, cmd);
        newEntity.setId(null);
        resolveId(newEntity, cmd);
        
        setFTS(newEntity);
        repository().save(newEntity);
        return toDto(newEntity);
    }

    /* ---------------------- BULK DELETE ---------------------- */
    public void bulkDelete(List<E> entities) {
        for (E entity : entities) {
            D oldValue = toDto(entity);
            repository().remove(entity);
            publishEventDelete(oldValue, toDto(entity));
        }
    }

    /* ---------------------- BULK APPROVE ---------------------- */
    public void bulkApprove(List<E> entities) {
        for (E entity : entities) {
            D oldValue = toDto(entity);
            E currentEffective = findEffectiveByCode(getCode(entity), entity.getAppCode(), entity.getTenantCode());
            if (currentEffective != null) closeCurrentEntity(currentEffective);
            MetaDataUtils.forUpdate(entity);
            entity.approveScdType2();
            repository().save(entity);
            publishEventApprove(oldValue, toDto(entity));
        }
    }

    /* ---------------------- INTERNAL UTILITIES ---------------------- */
    protected void setFTS(E entity) {
        entity.setFtsStringValue(FullTextSearchUtil.buildFTSString(entity));
    }

    protected void closeCurrentEntity(E entity) {
        MetaDataUtils.forUpdate(entity);
        entity.closeCurrentEntity();
        repository().save(entity);
        repository().flush();
    }

    protected E cloneUnauthorized(E entity) {
        E newEntity = cloneEntity(entity);
        newEntity.markAsUnauthorized();
        return newEntity;
    }
}