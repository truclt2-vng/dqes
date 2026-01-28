package com.a4b.dqes.domain;

import com.a4b.core.ag.ag_grid.annotation.FtsSearchable;
import com.a4b.dqes.domain.generic.BaseEntity;
import jakarta.persistence.*;
import jakarta.persistence.FetchType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "qrytb_data_type_op", schema = "dqes")
@Getter
@Setter
@NoArgsConstructor
@DynamicUpdate
@EqualsAndHashCode(callSuper = true)
@FtsSearchable
public class QrytbDataTypeOp extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @SequenceGenerator(name = "QRYTB_DATA_TYPE_OP_ID_GENERATOR", sequenceName = "qrytb_data_type_op_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "QRYTB_DATA_TYPE_OP_ID_GENERATOR")
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "int4")
    private Long id;

    @Column(name = "data_type_code", nullable = false)
    private String dataTypeCode;

    @Column(name = "op_code", nullable = false)
    private String opCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "op_code", referencedColumnName = "code", insertable = false, updatable = false)
    private QrytbOperationMeta opCodeRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_type_code", referencedColumnName = "code", insertable = false, updatable = false)
    private QrytbDataType dataTypeCodeRef;

}
