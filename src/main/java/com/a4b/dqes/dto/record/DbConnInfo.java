/**
 * Created: Jan 09, 2026 10:30:03 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.record;

public record DbConnInfo(
    int id,
    String tenantCode,
    String appCode,
    String connCode,
    String dbVendor,
    String host,
    int port,
    String dbName,
    String dbSchema,
    String username,
    String passwordEnc,
    String passwordAlg,
    Boolean sslEnabled,
    String sslMode,
    String jdbcParamsJson
) {}

