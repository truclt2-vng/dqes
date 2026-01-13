package com.a4b.dqes.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Entity for qrytb_operation_meta table
 * Stores metadata about query operators
 */
@Entity
@Table(name = "qrytb_operation_meta", schema = "dqes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationMeta implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;
    
    @Column(name = "app_code", nullable = false)
    private String appCode;
    
    @Column(name = "code", nullable = false)
    private String code; // EQ, NE, IN, BETWEEN, IS_NULL, EXISTS, NOT_EXISTS, etc.
    
    @Column(name = "op_symbol")
    private String opSymbol;
    
    @Column(name = "op_label", nullable = false)
    private String opLabel;
    
    @Column(name = "arity", nullable = false)
    private Integer arity = 1; // 0, 1, or 2
    
    @Column(name = "value_shape", nullable = false)
    private String valueShape = "SCALAR"; // SCALAR, ARRAY, RANGE, NONE
    
    @Column(name = "description", length = 2000)
    private String description;
}
