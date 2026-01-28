package com.a4b.dqes.dto.domain;

import com.a4b.dqes.dto.generic.ControllingFieldDto;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CfgtbDbconnInfoDto extends ControllingFieldDto{

    private Long id;

    private String connCode;
    private String connName;
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
    private JsonNode includeSchemas;
    private JsonNode includeTables;
    private JsonNode excludeTables;
    private String description;
}
