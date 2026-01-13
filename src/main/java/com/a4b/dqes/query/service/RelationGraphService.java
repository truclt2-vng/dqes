package com.a4b.dqes.query.service;

import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.query.model.RelationPath;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for resolving multi-hop paths in the relation graph
 * Uses Dijkstra's algorithm to find optimal paths considering weights
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelationGraphService {
    
    private final RelationInfoRepository relationInfoRepository;
    
    /**
     * Find the shortest path from source to target object using Dijkstra's algorithm
     * Cached for performance
     */
    @Cacheable(value = "queryPaths", key = "#tenantCode + '_' + #appCode + '_' + #dbconnId + '_' + #fromObject + '_' + #toObject")
    public Optional<RelationPath> findPath(
        String tenantCode,
        String appCode,
        Integer dbconnId,
        String fromObject,
        String toObject
    ) {
        if (fromObject.equals(toObject)) {
            return Optional.of(RelationPath.builder()
                .fromObject(fromObject)
                .toObject(toObject)
                .steps(new ArrayList<>())
                .totalWeight(0)
                .build());
        }
        
        // Get all navigable relations for this tenant/app/dbconn
        List<RelationInfo> relations = relationInfoRepository.findNavigableRelations(
            tenantCode, appCode, dbconnId
        );
        
        // Build adjacency map
        Map<String, List<RelationInfo>> adjacencyMap = new HashMap<>();
        for (RelationInfo relation : relations) {
            adjacencyMap.computeIfAbsent(relation.getFromObjectCode(), k -> new ArrayList<>())
                .add(relation);
        }
        
        // Dijkstra's algorithm
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        Map<String, RelationInfo> edgeMap = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(n -> n.distance));
        Set<String> visited = new HashSet<>();
        
        distances.put(fromObject, 0);
        queue.offer(new Node(fromObject, 0));
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            
            if (visited.contains(current.objectCode)) {
                continue;
            }
            
            visited.add(current.objectCode);
            
            if (current.objectCode.equals(toObject)) {
                break;
            }
            
            List<RelationInfo> neighbors = adjacencyMap.getOrDefault(current.objectCode, new ArrayList<>());
            for (RelationInfo relation : neighbors) {
                String neighbor = relation.getToObjectCode();
                int newDistance = current.distance + relation.getPathWeight();
                
                if (newDistance < distances.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    distances.put(neighbor, newDistance);
                    previous.put(neighbor, current.objectCode);
                    edgeMap.put(neighbor, relation);
                    queue.offer(new Node(neighbor, newDistance));
                }
            }
        }
        
        // Reconstruct path
        if (!previous.containsKey(toObject) && !fromObject.equals(toObject)) {
            log.warn("No path found from {} to {}", fromObject, toObject);
            return Optional.empty();
        }
        
        RelationPath path = RelationPath.builder()
            .fromObject(fromObject)
            .toObject(toObject)
            .steps(new ArrayList<>())
            .totalWeight(0)
            .build();
        
        List<String> pathObjects = new ArrayList<>();
        String current = toObject;
        while (!current.equals(fromObject)) {
            pathObjects.add(current);
            current = previous.get(current);
        }
        pathObjects.add(fromObject);
        Collections.reverse(pathObjects);
        
        for (int i = 0; i < pathObjects.size() - 1; i++) {
            String from = pathObjects.get(i);
            String to = pathObjects.get(i + 1);
            RelationInfo relation = edgeMap.get(to);
            
            path.addStep(RelationPath.PathStep.builder()
                .fromObject(from)
                .toObject(to)
                .toAlias(relation.getJoinAlias())
                .relationCode(relation.getCode())
                .joinType(relation.getJoinType())
                .filterMode(relation.getFilterMode())
                .weight(relation.getPathWeight())
                .build());
        }
        
        log.debug("Found path from {} to {} with {} steps, weight: {}", 
            fromObject, toObject, path.getSteps().size(), path.getTotalWeight());
        
        return Optional.of(path);
    }
    
    private static class Node {
        String objectCode;
        int distance;
        
        Node(String objectCode, int distance) {
            this.objectCode = objectCode;
            this.distance = distance;
        }
    }
}
