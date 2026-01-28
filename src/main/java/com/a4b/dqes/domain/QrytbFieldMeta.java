package com.a4b.dqes.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Entity for qrytb_field_meta table
 * Stores metadata about fields in queryable objects with alias hints
 */
@Entity
@Table(name = "qrytb_field_meta", schema = "dqes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrytbFieldMeta implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;
    
    @Column(name = "app_code", nullable = false)
    private String appCode;
    
    @Column(name = "object_code", nullable = false)
    private String objectCode;
    
    @Column(name = "field_code", nullable = false)
    private String fieldCode;
    
    @Column(name = "field_label", nullable = false)
    private String fieldLabel;
    
    @Column(name = "alias_hint", nullable = false)
    private String aliasHint;
    
    @Column(name = "mapping_type", nullable = false)
    private String mappingType = "COLUMN"; // COLUMN or EXPR
    
    @Column(name = "column_name")
    private String columnName;
    
    @Column(name = "data_type", nullable = false)
    private String dataType;
    
    @Column(name = "not_null")
    private Boolean notNull = false;

    @Column(name = "default_select")
    private Boolean defaultSelect;

    @Column(name = "allow_select")
    private Boolean allowSelect;

    @Column(name = "allow_filter")
    private Boolean allowFilter;
    
    @Column(name = "allow_sort")
    private Boolean allowSort;
    
    @Column(name = "description", length = 2000)
    private String description;
    
    @Column(name = "dbconn_id", nullable = false)
    private Integer dbconnId;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;
}
