package com.a4b.dqes.domain;

import com.a4b.core.ag.ag_grid.annotation.FtsSearchable;
import com.a4b.dqes.domain.generic.BaseEntity;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "qrytb_data_type", schema = "dqes")
@Getter
@Setter
@NoArgsConstructor
@DynamicUpdate
@EqualsAndHashCode(callSuper = true)
@FtsSearchable
public class QrytbDataType extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @SequenceGenerator(name = "QRYTB_DATA_TYPE_ID_GENERATOR", sequenceName = "qrytb_data_type_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "QRYTB_DATA_TYPE_ID_GENERATOR")
    @Id
    @Column(name = "id", nullable = false, columnDefinition = "int4")
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "java_type")
    private String javaType;

    @Column(name = "pg_cast")
    private String pgCast;

    @Column(name = "description")
    private String description;

}
