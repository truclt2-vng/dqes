package com.a4b.dqes.query.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.domain.RelationJoinKey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query context containing resolved metadata and aliases
 * Thread-safe for concurrent field resolution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryContext {
    
    private String tenantCode;
    private String appCode;
    private Integer dbconnId;
    private String rootObject;
    private String rootTable; // Physical table name for root object

    @Builder.Default
    private Map<String, ObjectMeta> allObjectMetaMap = new ConcurrentHashMap<>();
    
    @Builder.Default
    private Map<String, String> objectAliases = new ConcurrentHashMap<>(); // objectCode -> alias
    
    @Builder.Default
    private Map<String, String> objectTables = new ConcurrentHashMap<>(); // objectCode -> table name
    
    @Builder.Default
    private Set<String> joinedObjects = ConcurrentHashMap.newKeySet();
    
    @Builder.Default
    private Map<String, RelationPath> relationPaths = new ConcurrentHashMap<>(); // objectCode -> path from root

    @Builder.Default
    private List<RelationInfo> allRelationInfos = new ArrayList<>();

    @Builder.Default
    List<RelationJoinKey> allJoinKeys = new ArrayList<>();
    
    @Builder.Default
    private int aliasCounter = 0;
    
    /**
     * Get or generate a unique alias for an object
     */
    public synchronized String getOrGenerateAlias(String objectCode, String aliasHint) {
        return objectAliases.computeIfAbsent(objectCode, k -> {
            String baseAlias = aliasHint != null ? aliasHint : "t";
            String alias = baseAlias + (aliasCounter++);
            return alias;
        });
    }
    
    /**
     * Check if an object has been joined
     */
    public boolean isJoined(String objectCode) {
        return joinedObjects.contains(objectCode);
    }
    
    /**
     * Mark an object as joined
     */
    public void markJoined(String objectCode) {
        joinedObjects.add(objectCode);
    }
    
    /**
     * Register table name for an object
     */
    public void registerObjectTable(String objectCode, String tableName) {
        objectTables.put(objectCode, tableName);
    }
    
    /**
     * Get table name for an object
     */
    public String getObjectTable(String objectCode) {
        return objectTables.getOrDefault(objectCode, "dqes." + objectCode);
    }
}
