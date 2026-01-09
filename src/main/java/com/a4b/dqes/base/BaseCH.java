/**
 * Created: Jun 24, 2025 12:34:35 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.base;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.factory.annotation.Autowired;

import com.a4b.lib.shared.common.ActAction;
import com.a4b.lib.shared.deserializer.NullFields;
import com.a4b.lib.shared.util.CacheObjectUtil;
import com.a4b.lib.shared.validator.annotation.RefCode;
import com.a4b.dqes.aop.event.EventPublishingStore;
import com.a4b.dqes.dto.generic.ControllingFieldDto;
import com.a4b.dqes.event.XpprjtempEvent;
import com.a4b.dqes.security.SecurityUtils;

public abstract class BaseCH {

    @Autowired
    protected CommandGateway commandGateway;

    protected String eventType(){
        return "Base%sEvent";
    }

    protected String objectType(){
        return "BaseObjectType";
    }

    protected Boolean needApproval() {
        return false;
    }
    
    public void resolveId(Object targetObj, Object cmd) {
        targetObj = unproxy(targetObj);
        Class<?> clazz = cmd.getClass();

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                field.setAccessible(true);

                RefCode annotation = field.getAnnotation(RefCode.class);
                if (annotation == null) continue;

                try {
                    String fieldNameCode = field.getName();
                    String codeValue = (String) field.get(cmd);

                    String targetName = annotation.target().isEmpty()
                        ? fieldNameCode.replace("Code", "Id")
                        : annotation.target();
                    if (codeValue == null || codeValue.isEmpty()){
                        if(cmd instanceof NullFields nullFields) {
                            if(nullFields.getNullValueFields() != null 
                                && nullFields.getNullValueFields().size() > 0
                                && nullFields.getNullValueFields().contains(fieldNameCode)){
                                setTargetValue(targetObj, targetName, fieldNameCode, null);
                            }
                        }
                    }else {
                        Object cached = CacheObjectUtil.getCache(fieldNameCode, codeValue);
                        if (cached != null) {
                            try {
                                Method getIdMethod = cached.getClass().getMethod("getId");
                                Object idObj = getIdMethod.invoke(cached);
                                if (idObj instanceof Long id) {
                                    setTargetValue(targetObj, targetName, fieldNameCode, id);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to extract ID from cached object", e);
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to resolve " + field.getName(), e);
                }
            }
        }
    }

    private void setTargetValue(Object targetObj, String targetName, String fieldNameCode, Object value) {
        try {
            Field targetField = targetObj.getClass().getDeclaredField(targetName);
            targetField.setAccessible(true);
            targetField.set(targetObj, value);

        } catch (Exception e) {
            throw new RuntimeException("Failed to set value for target field: " + fieldNameCode, e);
        }
    }
    private <T> T unproxy(T entity) {
        if(entity == null) {
            return null;
        }
        if (entity instanceof HibernateProxy) {
            return (T) Hibernate.unproxy(entity);
        }
        return entity;
    }
    protected <D extends ControllingFieldDto> void publishEventCreate(D newValue) {
        publishEvent(
            ActAction.CREATE, 
            ActAction.CREATED, 
            objectType(), 
            null, 
            newValue
        );
    }

    protected <D extends ControllingFieldDto> void publishEventUpdate( D oldValue, D newValue) {
        publishEvent(
            ActAction.UPDATE, 
            ActAction.UPDATED, 
            objectType(), 
            oldValue, 
            newValue
        );
    }

    protected <D extends ControllingFieldDto> void publishEventUpdateStatus( D oldValue, D newValue) {
        publishEvent(
            ActAction.UPDATE_STATUS, 
            ActAction.STATUS_UPDATED, 
            objectType(), 
            oldValue, 
            newValue
        );
    }

    protected <D extends ControllingFieldDto> void publishEventDelete(D oldValue, D newValue) {
        publishEvent(
            ActAction.DELETE, 
            ActAction.DELETED, 
            objectType(), 
            oldValue, 
            newValue
        );
    }

    protected <D extends ControllingFieldDto> void publishEventApprove(D oldValue, D newValue) {
        publishEvent(
            ActAction.APPROVE, 
            ActAction.APPROVED, 
            objectType(), 
            oldValue, 
            newValue
        );
    }

    protected <D extends ControllingFieldDto> void publishEvent(String action, String eventName, String objectType, D oldValue, D newValue) {
        EventPublishingStore.putEvent(XpprjtempEvent.builder()
            .action(action)
            .objId(newValue.getAggId().toString())
            .tenantCode(newValue.getTenantCode())
            .appCode(newValue.getAppCode())
            .revision(newValue.getModNo() != null ? newValue.getModNo().intValue() : 0)
            .newValue(newValue)
            .oldValue(oldValue)
            .userId(newValue.getMakerId())
            .time(new Date())
            .objectType(objectType)
            .eventType(eventType().formatted(eventName))
            .rootObjType(objectType)
            .rootObjId(newValue.getAggId().toString())
            .build());
    }

    protected <D extends ControllingFieldDto> void publishEvent(String action, String eventName, String objectType, D oldValue, D newValue, String rootObjType, String rootId) {
        EventPublishingStore.putEvent(XpprjtempEvent.builder()
            .action(action)
            .objId(newValue.getAggId().toString())
            .tenantCode(newValue.getTenantCode())
            .appCode(newValue.getAppCode())
            .revision(newValue.getModNo() != null ? newValue.getModNo().intValue() : 0)
            .newValue(newValue)
            .oldValue(oldValue)
            .userId(newValue.getMakerId())
            .time(new Date())
            .objectType(objectType)
            .eventType(eventType().formatted(eventName))
            .rootObjType(rootObjType)
            .rootObjId(rootId)
            .build());
    }

    protected String appCode() {
        return SecurityUtils.getCurrentAppCode();
    }

    protected String tenantCode() {
        return SecurityUtils.getCurrentUserTenantCode();
    }
}
