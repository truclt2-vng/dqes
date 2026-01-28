package com.a4b.dqes.domain;

import com.a4b.core.ag.ag_grid.annotation.FtsSearchable;
import com.a4b.dqes.domain.generic.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.persistence.FetchType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "qrytb_expr_allowlist", schema = "dqes")
@Getter
@Setter
@NoArgsConstructor
@DynamicUpdate
@EqualsAndHashCode(callSuper = true)
@FtsSearchable
public class QrytbExprAllowlist extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @SequenceGenerator(name = "QRYTB_EXPR_ALLOWLIST_ID_GENERATOR", sequenceName = "qrytb_expr_allowlist_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "QRYTB_EXPR_ALLOWLIST_ID_GENERATOR")
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "int4")
    private Long id;

    @Column(name = "expr_code", nullable = false)
    private String exprCode;

    @Column(name = "expr_type", nullable = false)
    private String exprType;

    @Column(name = "sql_template", nullable = false)
    private String sqlTemplate;

    @Column(name = "allow_in_select", nullable = false)
    private Boolean allowInSelect;

    @Column(name = "allow_in_filter", nullable = false)
    private Boolean allowInFilter;

    @Column(name = "allow_in_sort", nullable = false)
    private Boolean allowInSort;

    @Column(name = "min_args", nullable = false, columnDefinition = "int4")
    private Long minArgs;

    @Column(name = "max_args", nullable = false, columnDefinition = "int4")
    private Long maxArgs;

    @Column(name = "args_spec", columnDefinition = "jsonb")
    @Type(JsonType.class)
    private JsonNode argsSpec;

    @Column(name = "return_data_type")
    private String returnDataType;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_data_type", referencedColumnName = "code", insertable = false, updatable = false)
    private QrytbDataType returnDataTypeRef;

}
