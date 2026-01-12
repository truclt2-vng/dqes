package com.a4b.dqes.query.store;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.a4b.dqes.query.meta.FieldMeta;
import com.a4b.dqes.query.meta.ObjectMeta;
import com.a4b.dqes.query.meta.OperationMeta;
import com.a4b.dqes.query.meta.RelationInfo;
import com.a4b.dqes.query.meta.RelationJoinKey;

public interface DqesMetadataStore {

    Map<String, ObjectMeta> loadObjectMeta(String tenant, String app, int dbconnId, Set<String> objectCodes);

    List<RelationInfo> loadRelations(String tenant, String app, int dbconnId);

    Map<Integer, List<RelationJoinKey>> loadJoinKeysByRelationIds(int dbconnId, Set<Integer> relationIds);

    Map<String, FieldMeta> loadFieldMeta(String tenant, String app, int dbconnId, Set<FieldKey> fieldKeys);

    Map<String, OperationMeta> loadOperationMeta(String tenant, String app, Set<String> opCodes);

    boolean isOperatorAllowedForDataType(String tenant, String app, String dataTypeCode, String opCode);

    record FieldKey(String objectCode, String fieldCode) {}
}
