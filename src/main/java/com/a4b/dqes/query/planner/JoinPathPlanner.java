package com.a4b.dqes.query.planner;

import com.a4b.dqes.query.ast.FilterNode;
import com.a4b.dqes.query.ast.JoinNode;
import com.a4b.dqes.query.ast.JoinNode.JoinPredicate;
import com.a4b.dqes.query.ast.JoinNode.JoinStrategy;
import com.a4b.dqes.query.ast.JoinNode.JoinType;
import com.a4b.dqes.query.ast.QueryAST;
import com.a4b.dqes.query.ast.SelectNode;
import com.a4b.dqes.query.ast.SortNode;
import com.a4b.dqes.query.metadata.DqesMetadataRepository;
import com.a4b.dqes.query.metadata.ObjectPathCache;
import com.a4b.dqes.query.metadata.RelationMeta;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JoinPathPlanner - Plans multi-hop JOIN graph using BFS + depends_on ordering
 * 
 * Strategy:
 * 1. Identify all required objects from SELECT/WHERE/ORDER BY
 * 2. Use qrytb_object_path_cache for shortest paths (pre-computed via BFS)
 * 3. Resolve relation dependencies (depends_on_code) for topological order
 * 4. Apply EXISTS strategy for ONE_TO_MANY filter-only relations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JoinPathPlanner {
    
    private final DqesMetadataRepository metadataRepo;
    
    /**
     * Plan JOIN graph for the query AST
     * Mutates QueryAST.joins with computed JoinNodes
     */
    public void planJoins(QueryAST ast) {
        String tenantCode = ast.getTenantCode();
        String appCode = ast.getAppCode();
        String rootObject = ast.getRootObject();
        
        // 1. Collect all referenced objects
        Set<String> referencedObjects = collectReferencedObjects(ast);
        referencedObjects.remove(rootObject); // Root doesn't need JOIN
        
        if (referencedObjects.isEmpty()) {
            log.debug("No joins needed for root object: {}", rootObject);
            return;
        }
        
        log.debug("Planning joins from root={} to objects={}", rootObject, referencedObjects);
        
        // 2. Find shortest paths using pre-computed cache
        Map<String, ObjectPathCache> pathCache = new HashMap<>();
        // Fetch ALL cached paths that start from rootObject
        List<ObjectPathCache> allPathCacheFromRoot = metadataRepo.findObjectPath(tenantCode, appCode, rootObject).orElse(List.of());

        // Index “best” (shortest) path per target object.
        // Pick the comparator that matches your schema (examples below).
        Map<String, ObjectPathCache> bestByTarget = allPathCacheFromRoot.stream()
            .filter(p -> rootObject.equals(p.getFromObjectCode()))
            .collect(java.util.stream.Collectors.toMap(
                ObjectPathCache::getToObjectCode,
                p -> p
            ));

        for (String targetObject : referencedObjects) {
            ObjectPathCache best = bestByTarget.get(targetObject);
            
            if (best == null) {
                throw new IllegalStateException(
                    "No navigation path found from " + rootObject + " to " + targetObject +
                    ". Run refresh_qry_object_paths() procedure."
                );
            }

            pathCache.put(targetObject, best);
        }
        
        // 3. Collect all relation codes from paths
        Set<String> requiredRelationCodes = pathCache.values().stream()
            .flatMap(p -> p.getPathRelationCodes().stream())
            .collect(Collectors.toSet());
        
        List<String> requiredRelationCodeList = new ArrayList<>(requiredRelationCodes);
        
        // 4. Load relation metadata with join keys
        // Fetch ALL cached paths that start from rootObject
        List<RelationMeta> allRequiredRelation = metadataRepo.findRelationMeta(tenantCode, appCode, requiredRelationCodeList).orElse(List.of());
        Map<String, RelationMeta> relationMetaMap = allRequiredRelation.stream()
            .filter(r -> r.getCode() != null && !r.getCode().isBlank())
            .collect(Collectors.toMap(
                RelationMeta::getCode,     // key = code
                r -> r,                    // value
                (a, b) -> a                // nếu trùng code thì giữ bản đầu (hoặc đổi b)
            ));

        
        // 5. Build JoinNodes from relations
        List<JoinNode> joinNodes = new ArrayList<>();
        for (RelationMeta rel : relationMetaMap.values()) {
            JoinNode joinNode = buildJoinNode(rel, ast);
            joinNodes.add(joinNode);
        }
        
        // 6. Topological sort based on depends_on_code
        List<JoinNode> sortedJoins = topologicalSort(joinNodes, relationMetaMap);
        
        // 7. Set execution order
        for (int i = 0; i < sortedJoins.size(); i++) {
            sortedJoins.get(i).setExecutionOrder(i);
        }
        
        ast.setJoins(sortedJoins);
        
        log.debug("Planned {} joins with execution order", sortedJoins.size());
    }
    
    /**
     * Collect all object codes referenced in query
     */
    private Set<String> collectReferencedObjects(QueryAST ast) {
        Set<String> objects = new HashSet<>();
        
        // From SELECT
        for (SelectNode select : ast.getSelects()) {
            objects.add(select.getObjectCode());
        }
        
        // From WHERE
        for (FilterNode filter : ast.getFilters()) {
            objects.add(filter.getObjectCode());
        }
        
        // From ORDER BY
        for (SortNode sort : ast.getSorts()) {
            objects.add(sort.getObjectCode());
        }
        
        return objects;
    }
    
    /**
     * Build JoinNode from RelationMeta
     * Apply EXISTS strategy for ONE_TO_MANY filter-only relations
     */
    private JoinNode buildJoinNode(RelationMeta rel, QueryAST ast) {
        JoinNode joinNode = new JoinNode();
        joinNode.setRelationCode(rel.getCode());
        joinNode.setFromObjectCode(rel.getFromObjectCode());
        joinNode.setToObjectCode(rel.getToObjectCode());
        joinNode.setJoinType(rel.getJoinType() == RelationMeta.JoinType.INNER ? JoinType.INNER : JoinType.LEFT);
        joinNode.setDependsOnRelationCode(rel.getDependsOnCode());
        
        // Determine JOIN vs EXISTS strategy
        JoinStrategy strategy = determineJoinStrategy(rel, ast);
        joinNode.setStrategy(strategy);
        
        // Map join keys
        List<JoinPredicate> predicates = rel.getJoinKeys().stream()
            .map(jk -> new JoinPredicate(
                jk.getFromColumnName(),
                jk.getOperator(),
                jk.getToColumnName(),
                jk.getNullSafe()
            ))
            .collect(Collectors.toList());
        
        joinNode.setPredicates(predicates);
        
        log.debug("Built JoinNode: {} -> {} (strategy={})", 
            rel.getFromObjectCode(), rel.getToObjectCode(), strategy);
        
        return joinNode;
    }
    
    /**
     * Determine JOIN vs EXISTS strategy
     * 
     * Rules:
     * - EXISTS_ONLY: Always use EXISTS
     * - EXISTS_PREFERRED: Use EXISTS if object is filter-only (not selected/sorted)
     * - AUTO: Use EXISTS for ONE_TO_MANY filter-only
     * - JOIN_ONLY: Always use JOIN
     */
    private JoinStrategy determineJoinStrategy(RelationMeta rel, QueryAST ast) {
        String toObject = rel.getToObjectCode();
        RelationMeta.FilterMode filterMode = rel.getFilterMode();
        
        // Force EXISTS
        if (filterMode == RelationMeta.FilterMode.EXISTS_ONLY) {
            return JoinStrategy.EXISTS_ONLY;
        }
        
        // Check if object is used in SELECT or ORDER BY
        boolean isSelected = ast.getSelects().stream()
            .anyMatch(s -> s.getObjectCode().equals(toObject));
        boolean isSorted = ast.getSorts().stream()
            .anyMatch(s -> s.getObjectCode().equals(toObject));
        
        boolean isUsedInOutput = isSelected || isSorted;
        
        // Force JOIN
        if (filterMode == RelationMeta.FilterMode.JOIN_ONLY) {
            return JoinStrategy.JOIN;
        }
        
        // EXISTS_PREFERRED: Use EXISTS if filter-only
        if (filterMode == RelationMeta.FilterMode.EXISTS_PREFERRED && !isUsedInOutput) {
            return JoinStrategy.EXISTS;
        }
        
        // AUTO mode: Use EXISTS for ONE_TO_MANY filter-only
        if (filterMode == RelationMeta.FilterMode.AUTO) {
            if (rel.getRelationType() == RelationMeta.RelationType.ONE_TO_MANY && !isUsedInOutput) {
                return JoinStrategy.EXISTS;
            }
        }
        
        // Default: standard JOIN
        return JoinStrategy.JOIN;
    }
    
    /**
     * Topological sort based on depends_on_code
     * Ensures dependencies are joined before dependents
     */
    private List<JoinNode> topologicalSort(List<JoinNode> joins, Map<String, RelationMeta> metaMap) {
        // Build dependency graph
        Map<String, List<String>> dependencyGraph = new HashMap<>();
        Map<String, JoinNode> nodeMap = new HashMap<>();
        
        for (JoinNode join : joins) {
            nodeMap.put(join.getRelationCode(), join);
            dependencyGraph.put(join.getRelationCode(), new ArrayList<>());
        }
        
        // Add edges: dependsOn -> relation
        for (JoinNode join : joins) {
            if (join.getDependsOnRelationCode() != null && 
                nodeMap.containsKey(join.getDependsOnRelationCode())) {
                dependencyGraph.get(join.getDependsOnRelationCode()).add(join.getRelationCode());
            }
        }
        
        // Kahn's algorithm for topological sort
        Map<String, Integer> inDegree = new HashMap<>();
        for (String rel : nodeMap.keySet()) {
            inDegree.put(rel, 0);
        }
        
        for (List<String> deps : dependencyGraph.values()) {
            for (String dep : deps) {
                inDegree.put(dep, inDegree.get(dep) + 1);
            }
        }
        
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        List<JoinNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(nodeMap.get(current));
            
            for (String dependent : dependencyGraph.get(current)) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                if (newDegree == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        // Check for cycles
        if (sorted.size() != joins.size()) {
            log.warn("Circular dependency detected in relation depends_on graph. Using original order.");
            return joins;
        }
        
        return sorted;
    }
}
