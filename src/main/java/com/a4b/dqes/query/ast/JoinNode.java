package com.a4b.dqes.query.ast;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a JOIN in the query plan
 * Computed by JoinPathPlanner based on relation graph
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinNode {
    private String relationCode;        // From qrytb_relation_info
    private String fromObjectCode;      // Left side of join
    private String toObjectCode;        // Right side of join
    private JoinType joinType;          // INNER/LEFT
    private JoinStrategy strategy;      // JOIN/EXISTS
    
    // Join predicates from qrytb_relation_join_key
    private List<JoinPredicate> predicates = new ArrayList<>();
    
    // Runtime alias allocation (set by SQL generator)
    private String fromAlias;
    private String toAlias;
    
    // Dependency order (for topological sort)
    private String dependsOnRelationCode;
    private int executionOrder;
    
    public JoinNode(String relationCode, String fromObjectCode, String toObjectCode, JoinType joinType) {
        this.relationCode = relationCode;
        this.fromObjectCode = fromObjectCode;
        this.toObjectCode = toObjectCode;
        this.joinType = joinType;
        this.strategy = JoinStrategy.JOIN;
    }
    
    public void addPredicate(JoinPredicate predicate) {
        this.predicates.add(predicate);
    }
    
    public enum JoinType {
        INNER, LEFT
    }
    
    public enum JoinStrategy {
        JOIN,           // Standard JOIN
        EXISTS,         // EXISTS subquery (for ONE_TO_MANY filters)
        EXISTS_ONLY     // Force EXISTS regardless of usage
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinPredicate {
        private String fromColumn;      // Column name (no alias)
        private String operator;        // =, !=, <, >, etc.
        private String toColumn;        // Column name (no alias)
        private boolean nullSafe;       // Use IS NOT DISTINCT FROM
        
        public JoinPredicate(String fromColumn, String toColumn) {
            this(fromColumn, "=", toColumn, false);
        }
    }
}
