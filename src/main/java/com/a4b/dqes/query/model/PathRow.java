/**
 * Created: Jan 16, 2026 1:23:16 PM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.query.model;

import com.fasterxml.jackson.databind.JsonNode;

public record PathRow(
            String relCode,
            String fromObjectCode,
            String toObjectCode,
            String joinAlias,
            int hopCount,
            int totalWeight,
            JsonNode pathRelationCodesJson
    ) {}
