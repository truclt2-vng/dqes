package com.a4b.dqes.query.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.a4b.dqes.query.meta.FieldMappingType;
import com.a4b.dqes.query.meta.FieldMeta;
import com.a4b.dqes.query.meta.FilterMode;
import com.a4b.dqes.query.meta.JoinType;
import com.a4b.dqes.query.meta.ObjectMeta;
import com.a4b.dqes.query.meta.OperationMeta;
import com.a4b.dqes.query.meta.RelationInfo;
import com.a4b.dqes.query.meta.RelationJoinKey;
import com.a4b.dqes.query.meta.RelationType;

/**
 * JDBC store for dqes schema (PostgreSQL).
 * Uses tenant_code/app_code/current_flg filters.
 */
public final class JdbcDqesMetadataStore implements DqesMetadataStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDqesMetadataStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    private static final RowMapper<ObjectMeta> OBJ_M = (rs, n) -> new ObjectMeta(
            rs.getString("object_code"),
            rs.getString("db_table"),
            rs.getString("alias_hint")
    );

    private static final RowMapper<RelationInfo> REL_M = (rs, n) -> new RelationInfo(
            rs.getInt("id"),
            rs.getString("code"),
            rs.getString("from_object_code"),
            rs.getString("to_object_code"),
            RelationType.valueOf(rs.getString("relation_type")),
            JoinType.valueOf(rs.getString("join_type")),
            FilterMode.valueOf(rs.getString("filter_mode")),
            rs.getBoolean("is_required"),
            rs.getBoolean("is_navigable"),
            rs.getInt("path_weight")
    );

    private static final RowMapper<RelationJoinKey> KEY_M = (rs, n) -> new RelationJoinKey(
            rs.getInt("relation_id"),
            rs.getInt("seq"),
            rs.getString("from_column_name"),
            rs.getString("operator"),
            rs.getString("to_column_name"),
            rs.getBoolean("null_safe")
    );

    private static final RowMapper<FieldMeta> FIELD_M = (rs, n) -> new FieldMeta(
            rs.getString("object_code"),
            rs.getString("field_code"),
            rs.getString("alias_hint"),
            FieldMappingType.valueOf(rs.getString("mapping_type")),
            rs.getString("column_name"),
            rs.getString("data_type"),
            rs.getBoolean("allow_select"),
            rs.getBoolean("allow_filter"),
            rs.getBoolean("allow_sort")
    );

    private static final RowMapper<OperationMeta> OP_M = (rs, n) -> new OperationMeta(
            rs.getString("code"),
            rs.getString("op_symbol"),
            rs.getInt("arity"),
            rs.getString("value_shape")
    );

    @Override
    public Map<String, ObjectMeta> loadObjectMeta(String tenant, String app, int dbconnId, Set<String> objectCodes) {
        if (objectCodes == null || objectCodes.isEmpty()) return Map.of();

        String sql = """
            SELECT object_code, db_table, alias_hint
            FROM dqes.qrytb_object_meta
            WHERE tenant_code = :tenant
              AND app_code = :app
              AND dbconn_id = :dbconnId
              AND current_flg = true
              AND object_code IN (:codes)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("tenant", tenant);
        params.put("app", app);
        params.put("dbconnId", dbconnId);
        params.put("codes", objectCodes);

        List<ObjectMeta> list = jdbc.query(sql, params, OBJ_M);

        Map<String, ObjectMeta> out = new HashMap<>();
        for (ObjectMeta o : list) out.put(o.objectCode(), o);
        return out;
    }

    @Override
    public List<RelationInfo> loadRelations(String tenant, String app, int dbconnId) {
        String sql = """
            SELECT id, code, from_object_code, to_object_code,
                   relation_type, join_type, filter_mode,
                   COALESCE(is_required,false) AS is_required,
                   COALESCE(is_navigable,true) AS is_navigable,
                   path_weight
            FROM dqes.qrytb_relation_info
            WHERE tenant_code = :tenant
              AND app_code = :app
              AND dbconn_id = :dbconnId
              AND current_flg = true
            """;
        return jdbc.query(sql, Map.of("tenant", tenant, "app", app, "dbconnId", dbconnId), REL_M);
    }

    @Override
    public Map<Integer, List<RelationJoinKey>> loadJoinKeysByRelationIds(int dbconnId, Set<Integer> relationIds) {
        if (relationIds == null || relationIds.isEmpty()) return Map.of();

        String sql = """
            SELECT relation_id, seq, from_column_name, operator, to_column_name, null_safe
            FROM dqes.qrytb_relation_join_key
            WHERE dbconn_id = :dbconnId
              AND relation_id IN (:ids)
              AND current_flg = true
            ORDER BY relation_id, seq
            """;

        List<RelationJoinKey> keys = jdbc.query(sql, Map.of("dbconnId", dbconnId, "ids", relationIds), KEY_M);

        return keys.stream().collect(Collectors.groupingBy(RelationJoinKey::relationId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public Map<String, FieldMeta> loadFieldMeta(String tenant, String app, int dbconnId, Set<FieldKey> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) return Map.of();

        List<String> objs = fieldKeys.stream().map(FieldKey::objectCode).distinct().toList();
        List<String> flds = fieldKeys.stream().map(FieldKey::fieldCode).distinct().toList();

        // NOTE: returns a superset, then filter in Java by (object_code, field_code)
        String sql = """
            SELECT object_code, field_code, alias_hint, mapping_type, column_name, data_type,
                   COALESCE(allow_select,false) AS allow_select,
                   COALESCE(allow_filter,true)  AS allow_filter,
                   COALESCE(allow_sort,true)    AS allow_sort
            FROM dqes.qrytb_field_meta
            WHERE tenant_code = :tenant
              AND app_code = :app
              AND current_flg = true
              AND object_code IN (:objs)
            """;

        List<FieldMeta> list = jdbc.query(sql, Map.of("tenant", tenant, "app", app, "objs", objs), FIELD_M);

        Map<String, FieldMeta> out = new HashMap<>();
        for (FieldMeta f : list) out.put(key(f.objectCode(), f.fieldCode()), f);

        Set<String> requested = fieldKeys.stream().map(k -> key(k.objectCode(), k.fieldCode())).collect(Collectors.toSet());
        out.keySet().retainAll(requested);
        return out;
    }

    @Override
    public Map<String, OperationMeta> loadOperationMeta(String tenant, String app, Set<String> opCodes) {
        if (opCodes == null || opCodes.isEmpty()) return Map.of();

        String sql = """
            SELECT code, op_symbol, arity, value_shape
            FROM dqes.qrytb_operation_meta
            WHERE tenant_code = :tenant
              AND app_code = :app
              AND current_flg = true
              AND code IN (:codes)
            """;

        List<OperationMeta> list = jdbc.query(sql, Map.of("tenant", tenant, "app", app, "codes", opCodes), OP_M);

        Map<String, OperationMeta> out = new HashMap<>();
        for (OperationMeta o : list) out.put(o.code(), o);
        return out;
    }

    @Override
    public boolean isOperatorAllowedForDataType(String tenant, String app, String dataTypeCode, String opCode) {
        String sql = """
            SELECT 1
            FROM dqes.qrytb_data_type_op
            WHERE tenant_code = :tenant
              AND app_code = :app
              AND current_flg = true
              AND data_type_code = :dt
              AND op_code = :op
            LIMIT 1
            """;
        List<Integer> r = jdbc.query(sql, Map.of("tenant", tenant, "app", app, "dt", dataTypeCode, "op", opCode),
                (rs, n) -> rs.getInt(1));
        return !r.isEmpty();
    }

    private static String key(String obj, String field) {
        return obj + "." + field;
    }
}
