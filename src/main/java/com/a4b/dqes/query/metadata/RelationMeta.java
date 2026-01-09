package com.a4b.dqes.query.metadata;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for Relation (JOIN definition)
 * Maps to: dqes.qrytb_relation_info + qrytb_relation_join_key
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationMeta {
    private Integer id;
    private String tenantCode;
    private String appCode;
    private String code;
    private String fromObjectCode;
    private String toObjectCode;
    private RelationType relationType;
    private JoinType joinType;
    private FilterMode filterMode;
    private Boolean isRequired;
    private Boolean isNavigable;
    private Integer pathWeight;
    private String dependsOnCode;       // For dependency ordering
    private Integer dbconnId;
    
    // Join predicates from qrytb_relation_join_key
    private List<JoinKeyMeta> joinKeys;
    
    public enum RelationType {
        MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY
    }
    
    public enum JoinType {
        INNER, LEFT
    }
    
    public enum FilterMode {
        AUTO,               // Engine decides
        JOIN_ONLY,          // Always use JOIN
        EXISTS_PREFERRED,   // Prefer EXISTS for filter-only
        EXISTS_ONLY         // Always use EXISTS
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinKeyMeta {
        private Integer id;
        private Integer relationId;
        private Integer seq;
        private String fromColumnName;
        private String operator;
        private String toColumnName;
        private Boolean nullSafe;
    }
}
