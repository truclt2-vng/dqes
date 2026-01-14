package com.a4b.dqes.query.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a path in the relation graph from root to target object
 * Used for multi-hop join resolution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationPath {
    
    private String fromObject;
    private String toObject;
    private List<PathStep> steps;
    private Integer totalWeight;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathStep {
        private String fromObject;
        private String toObject;
        private String toAlias;
        
        /**
         * The join_alias from qrytb_relation_info
         * Used to differentiate multiple joins to the same table
         */
        private String joinAlias;
        
        private String relationCode;
        private String joinType;
        private String filterMode;
        private Integer weight;
    }
    
    public void addStep(PathStep step) {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        steps.add(step);
        if (totalWeight == null) {
            totalWeight = 0;
        }
        totalWeight += (step.getWeight() != null ? step.getWeight() : 10);
    }
}
