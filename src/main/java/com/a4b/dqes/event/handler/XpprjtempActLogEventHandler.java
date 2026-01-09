/**
 * Created: Oct 29, 2025 11:50:43 AM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.event.handler;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import com.a4b.activities.model.ActObject;
import com.a4b.xpactstreams.annotation.ActivitiStreamAlias;
import com.a4b.xpactstreams.interceptor.AuditKey;
import com.a4b.xpactstreams.interceptor.CurrentAuditContext;
import com.a4b.dqes.event.XpprjtempEvent;

@Component
public class XpprjtempActLogEventHandler {

    protected String buildPartitionKey(Date date) {
        return DateTimeFormatter.ofPattern("yyyyMM")
                .format(ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    @EventHandler
	@ActivitiStreamAlias(action = "OVERRIDE")
	public void handler(XpprjtempEvent event) {

		ActObject object = new ActObject(event.getObjectType(), event.getObjId(), event.getSummary(),
        event.getOldValue(), event.getNewValue());
        
        String category =  event.getCategory();
        String subCategory = event.getSubCategory();

        object.setRootObjectType(event.getRootObjType());
        object.setRootObjId(event.getRootObjId());

        CurrentAuditContext.get().put(AuditKey.ACT_ACTION.name(), event.getAction());
		CurrentAuditContext.get().put(AuditKey.ACT_OBJECT.name(), object);
		CurrentAuditContext.get().put(AuditKey.ACT_PARTITION_KEY.name(),buildPartitionKey(event.getTime()));
        CurrentAuditContext.get().put(AuditKey.ACT_CATEGORY.name(),  category);
        CurrentAuditContext.get().put(AuditKey.ACT_SUB_CATEGORY.name(), subCategory);
        CurrentAuditContext.get().put(AuditKey.ACT_ROOT_OBJ_TYPE.name(), event.getRootObjType());
        CurrentAuditContext.get().put(AuditKey.ACT_ROOT_OBJ_ID.name(), event.getRootObjId());
	}
    
}
