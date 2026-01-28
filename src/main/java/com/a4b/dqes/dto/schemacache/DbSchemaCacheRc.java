/**
 * Created: Jan 28, 2026 12:22:37 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbSchemaCacheRc {
    private List<ObjectMetaRC> objectMetas;
    private List<RelationInfoRC> relationInfos;
}