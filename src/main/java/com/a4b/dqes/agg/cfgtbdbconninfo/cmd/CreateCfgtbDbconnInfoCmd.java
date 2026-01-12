package com.a4b.dqes.agg.cfgtbdbconninfo.cmd;

import com.a4b.core.server.enums.RecordStatus;
import com.a4b.core.validation.constraints.StepByStep;
import com.a4b.core.validation.constraints.Validator;
import com.a4b.core.validation.constraints.group.GroupOrder;
import com.a4b.core.validation.constraints.group.Level1;
import com.a4b.core.validation.constraints.group.Level9;
import com.a4b.dqes.agg.cfgtbdbconninfo.CfgtbDbconnInfoValidator;
import java.io.Serializable;
import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
@Data
@GroupOrder
@StepByStep(value = { @Validator(value = CfgtbDbconnInfoValidator.class)}, groups = Level9.class)
public class CreateCfgtbDbconnInfoCmd implements Serializable{
    @NotNull(groups = Level1.class)
    private String connCode;

    @NotNull(groups = Level1.class)
    private String connName;

    @NotNull(groups = Level1.class)
    private String dbVendor;

    @NotNull(groups = Level1.class)
    private String host;

    @NotNull(groups = Level1.class)
    private Long port;

    @NotNull(groups = Level1.class)
    private String dbName;

    private String dbSchema;

    @NotNull(groups = Level1.class)
    private String username;
    
    @NotNull(groups = Level1.class)
    private String passwordPlain;

    @Hidden
    @JsonIgnore
    private String passwordEnc;
    
    @Hidden
    @JsonIgnore
    private String passwordAlg;

    private Boolean sslEnabled;

    private String sslMode;

    private JsonNode jdbcParams;

    private JsonNode includeSchemas;

    private JsonNode includeTables;

    private JsonNode excludeTables;

    private String description;

    @Schema(description = "Record status", example = "O (Open) or C (Closed)")
    private String recordStatus=RecordStatus.O.name();
}
