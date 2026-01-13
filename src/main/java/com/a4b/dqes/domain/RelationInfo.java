package com.a4b.dqes.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Entity for qrytb_relation_info table
 * Stores relationship metadata for multi-hop graph joins
 */
@Entity
@Table(name = "qrytb_relation_info", schema = "dqes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationInfo implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;
    
    @Column(name = "app_code", nullable = false)
    private String appCode;
    
    @Column(name = "code", nullable = false)
    private String code;
    
    @Column(name = "from_object_code", nullable = false)
    private String fromObjectCode;
    
    @Column(name = "to_object_code", nullable = false)
    private String toObjectCode;
    
    @Column(name = "relation_type", nullable = false)
    private String relationType; // MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY
    
    @Column(name = "join_type", nullable = false)
    private String joinType = "LEFT"; // INNER, LEFT
    
    @Column(name = "join_alias")
    private String joinAlias;
    
    @Column(name = "filter_mode", nullable = false)
    private String filterMode = "AUTO"; // AUTO, JOIN_ONLY, EXISTS_PREFERRED, EXISTS_ONLY
    
    @Column(name = "is_required")
    private Boolean isRequired = false;
    
    @Column(name = "is_navigable")
    private Boolean isNavigable = true;
    
    @Column(name = "path_weight", nullable = false)
    private Integer pathWeight = 10;
    
    @Column(name = "description", length = 2000)
    private String description;
    
    @Column(name = "dbconn_id", nullable = false)
    private Integer dbconnId;
}
