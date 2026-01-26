/**
 * Created: Jan 19, 2026 5:31:19 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

import java.util.List;

import com.a4b.dqes.domain.ObjectMeta;

import lombok.Data;

@Data
public class PlanRequest {
    private Integer connId;
    private ObjectMeta rootObject;
    List<FieldKey> selectFields;
    List<FieldKey> filterFields;

    public PlanRequest(Integer connId, ObjectMeta rootObject, List<FieldKey> selectFields, List<FieldKey> filterFields) {
        this.connId = connId;
        this.rootObject = rootObject;
        this.selectFields = selectFields;
        this.filterFields = filterFields;
    }
}
