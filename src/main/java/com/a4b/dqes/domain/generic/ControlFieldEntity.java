/**
 * Created: Jun 23, 2025 4:01:12 PM
 * Copyright © 2025 by A4B. All rights reserved
 */
package com.a4b.dqes.domain.generic;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class ControlFieldEntity implements Serializable{

    public abstract Long getId();
    public abstract void setId(Long id);

    @Column(name = "agg_id", nullable = false, columnDefinition = "uuid")
    private UUID aggId;
    
    @Column(name = "tenant_code")
    private String tenantCode;

    @Column(name = "app_code")
    private String appCode;

    @Column(name = "fts_string_value")
    private String ftsStringValue;

    @Column(name = "record_order")
    private Long recordOrder = 0L;

    @Column(name = "record_status")
    private String recordStatus = "O";

    @Column(name = "auth_status")
    private String authStatus = "A";

    @Column(name = "maker_id")
    private String makerId;

    @Column(name = "maker_date")
    private OffsetDateTime makerDate;

    @Column(name = "update_id")
    private String updateId;

    @Column(name = "update_date")
    private OffsetDateTime updateDate;

    @Column(name = "mod_no")
    @Version
    private Long modNo;

    @Column(name = "create_date")
    private OffsetDateTime createDate = OffsetDateTime.now();
}
