/**
 * Created: Jan 09, 2026 10:30:03 AM
 * Copyright © 2026 by A4B. All rights reserved
 */
package com.a4b.dqes.dto.schemacache;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbConnInfoDto {
    private Long id;
    private String tenantCode;
    private String appCode;
    private String connCode;
    private String dbVendor;
    private String host;
    private Long port;
    private String dbName;
    private String dbSchema;
    private String username;
    private String passwordEnc;
    private String passwordAlg;
    private Boolean sslEnabled;
    private String sslMode;
    private JsonNode jdbcParams;
} 

