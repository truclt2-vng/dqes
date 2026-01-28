package com.a4b.dqes.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Entity for qrytb_relation_join_key table
 * Stores join predicates for relations
 */
@Entity
@Table(name = "qrytb_relation_join_condition", schema = "dqes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrytbRelationJoinCondition implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "relation_code", nullable = false)
    private String relationCode;
    
    @Column(name = "seq", nullable = false)
    private Integer seq = 1;
    
    @Column(name = "column_name", nullable = false)
    private String columnName;
    
    @Column(name = "operator", nullable = false)
    private String operator = "=";
    
    //'CONST','PARAM','EXPR'
    @Column(name = "value_type", nullable = false)
    private String valueType="CONST";
    
    @Column(name = "value_literal")
    private String valueLiteral;

    @Column(name = "param_name")
    private String paramName;
    
    @Column(name = "description", length = 2000)
    private String description;
    
    @Column(name = "dbconn_id", nullable = false)
    private Integer dbconnId;
}
