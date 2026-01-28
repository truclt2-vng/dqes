/**
 * Created: Jan 26, 2026 10:55:12 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.planner;

import com.a4b.dqes.dto.schemacache.RelationInfoRC;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JoinStep {
    private String fromObjectCode;
    private String fromAlias;
    private String toObjectCode;
    private RelationInfoRC relationInfo;
    private String joinTable;
    private String runtimeAlias;
    private String relationType;

    @Builder.Default
    private int joinOrder=1;
}
