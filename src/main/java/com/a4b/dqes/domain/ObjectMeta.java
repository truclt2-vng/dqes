package com.a4b.dqes.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.List;

/**
 * Entity for qrytb_object_meta table
 * Stores metadata about queryable objects with alias hints
 */
@Entity
@Table(name = "qrytb_object_meta", schema = "dqes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMeta implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;
    
    @Column(name = "app_code", nullable = false)
    private String appCode;
    
    @Column(name = "object_code", nullable = false)
    private String objectCode;
    
    @Column(name = "object_name", nullable = false)
    private String objectName;
    
    @Column(name = "db_table", nullable = false)
    private String dbTable;
    
    @Column(name = "alias_hint", nullable = false)
    private String aliasHint;
    
    @Column(name = "dbconn_id", nullable = false)
    private Integer dbconnId;
    
    @Column(name = "description", length = 2000)
    private String description;

    @Transient
    private List<FieldMeta> fieldMetas;
}
