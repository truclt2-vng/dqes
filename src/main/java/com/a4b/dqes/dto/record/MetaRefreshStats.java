/**
 * Created: Jan 09, 2026 11:46:30 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.record;

public record MetaRefreshStats(
    int objectsInserted,
    int fieldsInserted,
    int relationsInserted,
    int joinKeysInserted
) {}

