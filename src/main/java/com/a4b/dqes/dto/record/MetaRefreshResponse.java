/**
 * Created: Jan 09, 2026 11:44:46 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.record;

public record MetaRefreshResponse(
    String tenantCode,
    String appCode,
    String connCode,
    long elapsedMs,
    int objectsInserted,
    int fieldsInserted,
    int relationsInserted,
    int joinKeysInserted,
    boolean pathCacheRefreshed
) {}

