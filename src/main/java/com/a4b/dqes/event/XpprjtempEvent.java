/**
 * Created: Dec 01, 2025 12:37:54 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.event;

import java.util.Date;

import com.a4b.activities.event.BaseActLogEventField;
import com.a4b.core.event.outbox.event.GenericTenantAppOutEvent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class XpprjtempEvent extends BaseActLogEventField implements GenericTenantAppOutEvent {
    private String id;

    private Object newValue;
    private Object oldValue;
    private String userId;
    private Date time;

    private Integer revision;
    private String appCode;
    private String tenantCode;
    private String objId;
    private String eventType;

    private String rootObjType;
    private String rootObjId;
    
}