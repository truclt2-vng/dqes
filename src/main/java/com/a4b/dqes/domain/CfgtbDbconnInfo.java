package com.a4b.dqes.domain;

import com.a4b.core.ag.ag_grid.annotation.FtsSearchable;
import com.a4b.dqes.domain.generic.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "cfgtb_dbconn_info", schema = "dqes")
@Getter
@Setter
@NoArgsConstructor
@DynamicUpdate
@EqualsAndHashCode(callSuper = true)
@FtsSearchable
public class CfgtbDbconnInfo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @SequenceGenerator(name = "CFGTB_DBCONN_INFO_ID_GENERATOR", sequenceName = "cfgtb_dbconn_info_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CFGTB_DBCONN_INFO_ID_GENERATOR")
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "int4")
    private Long id;

    @Column(name = "conn_code", nullable = false)
    private String connCode;

    @Column(name = "conn_name", nullable = false)
    private String connName;

    @Column(name = "db_vendor", nullable = false)
    private String dbVendor;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false, columnDefinition = "int4")
    private Long port;

    @Column(name = "db_name", nullable = false)
    private String dbName;

    @Column(name = "db_schema")
    private String dbSchema;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_enc", nullable = false)
    private String passwordEnc;

    @Column(name = "password_alg")
    private String passwordAlg;

    @Column(name = "ssl_enabled")
    private Boolean sslEnabled;

    @Column(name = "ssl_mode")
    private String sslMode;

    @Column(name = "jdbc_params", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode jdbcParams;

    @Column(name = "include_schemas", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode includeSchemas;

    @Column(name = "include_tables", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode includeTables;

    @Column(name = "exclude_tables", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode excludeTables;

    @Column(name = "description")
    private String description;

}
