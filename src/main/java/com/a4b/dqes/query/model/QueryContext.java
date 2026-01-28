package com.a4b.dqes.query.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.a4b.dqes.domain.QrytbObjectMeta;
import com.a4b.dqes.domain.QrytbRelationInfo;
import com.a4b.dqes.dto.schemacache.ObjectMetaRC;
import com.a4b.dqes.dto.schemacache.RelationInfoRC;
import com.a4b.dqes.query.planner.FieldKey;

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

    private boolean distinct;
    private Integer offset;
    private Integer limit;
    private boolean countOnly;

    private List<FieldKey> selectFields = new ArrayList<>();
    private List<FieldKey> filterFields = new ArrayList<>();

    @Builder.Default
    private Map<String, ObjectMetaRC> allObjectMetaMap = new ConcurrentHashMap<>();
    
    @Builder.Default
    private Map<String, String> objectAliases = new ConcurrentHashMap<>(); // objectCode -> alias

    @Builder.Default
    private Map<String, ObjectMetaRC> objectMetaPlan = new ConcurrentHashMap<>();
    
    @Builder.Default
    private Map<String, String> objectTables = new ConcurrentHashMap<>(); // objectCode -> table name
    
    @Builder.Default
    private List<RelationInfoRC> allRelationInfos = new ArrayList<>();

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
}
