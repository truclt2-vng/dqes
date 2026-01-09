/**
 * Created: Jan 09, 2026 11:43:41 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.record;

import jakarta.validation.constraints.NotBlank;

public record MetaRefreshRequest(
    @NotBlank String tenantCode,
    @NotBlank String appCode,
    @NotBlank String connCode,

    // optional
    Integer maxDepth,              // default 6
    Boolean includeViews,          // default true
    Boolean includeTables,         // default true
    Boolean includeMaterializedViews, // default true (if driver returns)
    Boolean generateViewRelations  // default false (views thường không có FK)
) {}

