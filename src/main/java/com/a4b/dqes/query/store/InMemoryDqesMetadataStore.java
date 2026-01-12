package com.a4b.dqes.query.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.a4b.dqes.query.meta.FieldMeta;
import com.a4b.dqes.query.meta.ObjectMeta;
import com.a4b.dqes.query.meta.OperationMeta;
import com.a4b.dqes.query.meta.RelationInfo;
import com.a4b.dqes.query.meta.RelationJoinKey;

public final class InMemoryDqesMetadataStore implements DqesMetadataStore {

    public final Map<String, ObjectMeta> objects = new HashMap<>();
    public final List<RelationInfo> relations = new ArrayList<>();
    public final Map<Integer, List<RelationJoinKey>> joinKeys = new HashMap<>();
    public final Map<String, FieldMeta> fields = new HashMap<>();
    public final Map<String, OperationMeta> ops = new HashMap<>();
    public final Set<String> typeOpAllowed = new HashSet<>();

    @Override
    public Map<String, ObjectMeta> loadObjectMeta(String tenant, String app, int dbconnId, Set<String> objectCodes) {
        return objects.entrySet().stream()
                .filter(e -> objectCodes.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public List<RelationInfo> loadRelations(String tenant, String app, int dbconnId) {
        return List.copyOf(relations);
    }

    @Override
    public Map<Integer, List<RelationJoinKey>> loadJoinKeysByRelationIds(int dbconnId, Set<Integer> relationIds) {
        Map<Integer, List<RelationJoinKey>> out = new HashMap<>();
        for (Integer id : relationIds) {
            List<RelationJoinKey> keys = joinKeys.get(id);
            if (keys != null) out.put(id, keys);
        }
        return out;
    }

    @Override
    public Map<String, FieldMeta> loadFieldMeta(String tenant, String app, int dbconnId, Set<FieldKey> fieldKeys) {
        Set<String> ks = fieldKeys.stream().map(k -> k.objectCode() + "." + k.fieldCode()).collect(Collectors.toSet());
        return fields.entrySet().stream()
                .filter(e -> ks.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Map<String, OperationMeta> loadOperationMeta(String tenant, String app, Set<String> opCodes) {
        return ops.entrySet().stream()
                .filter(e -> opCodes.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public boolean isOperatorAllowedForDataType(String tenant, String app, String dataTypeCode, String opCode) {
        return typeOpAllowed.contains(key(tenant, app, dataTypeCode, opCode));
    }

    public void allow(String tenant, String app, String dt, String op) {
        typeOpAllowed.add(key(tenant, app, dt, op));
    }

    private static String key(String t, String a, String dt, String op) {
        return t + "|" + a + "|" + dt + "|" + op;
    }
}
