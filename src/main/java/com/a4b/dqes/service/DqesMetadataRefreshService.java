package com.a4b.dqes.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.a4b.dqes.crypto.CryptoService;
import com.a4b.dqes.dto.record.DbConnInfo;
import com.a4b.dqes.dto.record.MetaRefreshStats;
import com.google.common.base.CaseFormat;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

// ...

@Service
public class DqesMetadataRefreshService {

    private final NamedParameterJdbcTemplate dqesJdbc;
    private final JdbcTemplate dqesPlainJdbc; // dùng batchUpdate nhanh hơn
    private final CryptoService cryptoService;

    private static final int BATCH_SIZE = 500;

    public DqesMetadataRefreshService(NamedParameterJdbcTemplate dqesJdbc, CryptoService cryptoService) {
        this.dqesJdbc = dqesJdbc;
        this.dqesPlainJdbc = dqesJdbc.getJdbcTemplate();
        this.cryptoService = cryptoService;
    }

    // =========================
    // DTO rows for batch
    // =========================
    private record ObjectRow(
            int dbconnId, String tenant, String app,
            String objectCode, String objectName, String dbTable, String aliasHint,
            String description, String fts
    ) {}

    private record FieldRow(
            String tenant, String app, String objectCode,
            String fieldCode, String fieldLabel,
            String columnName, String aliasHint, String dataType,
            boolean notNull, String description, String fts
    ) {}

    private record RelationRow(
            int dbconnId, String tenant, String app,
            String code, String fromObject, String toObject,
            String relationType  // MANY_TO_ONE or ONE_TO_MANY
    ) {}
    
    private record JoinKeyRow(
            int relationId, int seq,
            String fromColumn, String operator, String toColumn,
            boolean nullSafe,
            int dbconnId, String tenant, String app
    ) {}

    private static final class Stats {
        int objects, fields, relations, joinKeys;
        MetaRefreshStats toRecord() { return new MetaRefreshStats(objects, fields, relations, joinKeys); }
    }

    @Transactional
    public MetaRefreshStats refreshByConnCode(String tenantCode, String appCode, String connCode) throws Exception {
        DbConnInfo conn = loadConn(tenantCode, appCode, connCode);

        String passwordPlain = cryptoService.decrypt(conn.passwordEnc(), conn.passwordAlg());

        DataSource targetDs = buildTargetDataSource(conn, passwordPlain);

        // 1) purge existing metadata for this dbconn_id
        purgeExisting(conn.id(), tenantCode, appCode);

        Stats stats = new Stats();
        // 2) scan + insert objects/fields/relations
        try {
            scanAndPersist(targetDs, conn, tenantCode, appCode, stats);
        } finally {
            if (targetDs instanceof com.zaxxer.hikari.HikariDataSource hk) hk.close();
        }

        // 3) optional: refresh path cache
        dqesJdbc.getJdbcTemplate().execute(
                "CALL dqes.refresh_qry_object_paths(?, ?, ?, ?)",
                (PreparedStatementCallback<Void>) ps -> {
                    ps.setString(1, tenantCode);
                    ps.setString(2, appCode);
                    ps.setInt(3, conn.id());
                    ps.setInt(4, 6);
                    ps.execute();
                    return null;
                });
        return stats.toRecord();
    }

    private DbConnInfo loadConn(String tenantCode, String appCode, String connCode) {
        String sql = """
                SELECT id, tenant_code, app_code, conn_code, db_vendor, host, port, db_name, db_schema,
                       username, password_enc, password_alg, ssl_enabled, ssl_mode, jdbc_params::text
                FROM dqes.cfgtb_dbconn_info
                WHERE tenant_code=:tenant AND app_code=:app AND conn_code=:code
                  AND record_status <> 'D'
                """;

        Map<String, Object> p = Map.of("tenant", tenantCode, "app", appCode, "code", connCode);

        return dqesJdbc.queryForObject(sql, p, (rs, i) -> new DbConnInfo(
                rs.getInt("id"),
                rs.getString("tenant_code"),
                rs.getString("app_code"),
                rs.getString("conn_code"),
                rs.getString("db_vendor"),
                rs.getString("host"),
                rs.getInt("port"),
                rs.getString("db_name"),
                rs.getString("db_schema"),
                rs.getString("username"),
                rs.getString("password_enc"),
                rs.getString("password_alg"),
                (Boolean) rs.getObject("ssl_enabled"),
                rs.getString("ssl_mode"),
                rs.getString("jdbc_params")));
    }

    private void purgeExisting(int dbconnId, String tenantCode, String appCode) {
        dqesJdbc.update("""
                DELETE FROM dqes.qrytb_object_path_cache
                WHERE tenant_code=:tenant AND app_code=:app
                  AND dbconn_id=:dbconnId
                """, Map.of("tenant", tenantCode, "app", appCode, "dbconnId", dbconnId));
        // delete join keys -> relations -> fields -> objects
        dqesJdbc.update("""
                DELETE FROM dqes.qrytb_relation_join_key
                WHERE relation_id IN (
                  SELECT id FROM dqes.qrytb_relation_info
                  WHERE tenant_code=:tenant AND app_code=:app
                    AND (relation_props->>'dbconn_id')::int = :dbconnId
                )
                """, Map.of("tenant", tenantCode, "app", appCode, "dbconnId", dbconnId));

        dqesJdbc.update("""
                DELETE FROM dqes.qrytb_relation_info
                WHERE tenant_code=:tenant AND app_code=:app
                  AND (relation_props->>'dbconn_id')::int = :dbconnId
                """, Map.of("tenant", tenantCode, "app", appCode, "dbconnId", dbconnId));

        dqesJdbc.update("""
                DELETE FROM dqes.qrytb_field_meta
                WHERE tenant_code=:tenant AND app_code=:app
                  AND object_code IN (
                    SELECT object_code FROM dqes.qrytb_object_meta
                    WHERE tenant_code=:tenant AND app_code=:app AND dbconn_id=:dbconnId
                  )
                """, Map.of("tenant", tenantCode, "app", appCode, "dbconnId", dbconnId));

        dqesJdbc.update("""
                DELETE FROM dqes.qrytb_object_meta
                WHERE tenant_code=:tenant AND app_code=:app AND dbconn_id=:dbconnId
                """, Map.of("tenant", tenantCode, "app", appCode, "dbconnId", dbconnId));
    }

    private DataSource buildTargetDataSource(DbConnInfo c, String passwordPlain) {
        // Postgres example; anh có thể switch theo c.dbVendor()
        String schema = (c.dbSchema() == null || c.dbSchema().isBlank()) ? "public" : c.dbSchema();
        String url = "jdbc:postgresql://" + c.host() + ":" + c.port() + "/" + c.dbName();

        // append params
        // - currentSchema giúp getTables/getColumns ưu tiên đúng schema
        StringBuilder sb = new StringBuilder(url).append("?currentSchema=").append(schema);
        if (Boolean.TRUE.equals(c.sslEnabled())) {
            sb.append("&ssl=true");
            if (c.sslMode() != null && !c.sslMode().isBlank())
                sb.append("&sslmode=").append(c.sslMode());
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(sb.toString());
        cfg.setUsername(c.username());
        cfg.setPassword(passwordPlain);
        cfg.setMaximumPoolSize(2);
        cfg.setPoolName("dqes-target-" + c.connCode());
        return new HikariDataSource(cfg);
    }

    // =========================
    // scan + batch persist
    // =========================
    private void scanAndPersist(DataSource targetDs, DbConnInfo conn,
                                String tenantCode, String appCode, Stats stats) throws Exception {

        List<ObjectRow> objects = new ArrayList<>();
        List<FieldRow> fields = new ArrayList<>();
        List<RelationRow> relations = new ArrayList<>();
        List<PendingJoinKey> pendingJoinKeys = new ArrayList<>(); // relationCode + keys

        try (Connection cx = targetDs.getConnection()) {
            DatabaseMetaData md = cx.getMetaData();
            String schemaPattern = conn.dbSchema();
            String[] types = new String[] { "TABLE", "VIEW", "MATERIALIZED VIEW" };

            Map<String, String> objectCodeByTableKey = new HashMap<>();

            // 1) tables/views -> objects list
            try (ResultSet rs = md.getTables(cx.getCatalog(), schemaPattern, "%", types)) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    String name   = rs.getString("TABLE_NAME");
                    String type   = rs.getString("TABLE_TYPE");
                    if (schema == null || name == null) continue;

                    String objectCode = toObjectCode(schema, name);
                    String dbTable = schema + "." + name;

                    objects.add(new ObjectRow(
                            conn.id(), tenantCode, appCode,
                            objectCode, name, dbTable, aliasHint(name),
                            "Auto-generated from " + type,
                            objectCode + " " + name + " " + dbTable
                    ));
                    objectCodeByTableKey.put(dbTable, objectCode);
                }
            }

            // batch insert objects
            batchInsertObjects(objects);
            stats.objects += objects.size();

            // 2) columns -> fields list
            for (var entry : objectCodeByTableKey.entrySet()) {
                String tableKey = entry.getKey(); // schema.table
                String objectCode = entry.getValue();
                String[] parts = tableKey.split("\\.", 2);
                String schema = parts[0];
                String table  = parts[1];

                try (ResultSet cols = md.getColumns(cx.getCatalog(), schema, table, "%")) {
                    while (cols.next()) {
                        String colName = cols.getString("COLUMN_NAME");
                        int jdbcType   = cols.getInt("DATA_TYPE");
                        String typeName = cols.getString("TYPE_NAME");
                        int nullable   = cols.getInt("NULLABLE");
                        String remarks = cols.getString("REMARKS");

                        String dataTypeCode = mapToDqesDataType(jdbcType, typeName);
                        String fieldCode = toFieldCode(colName);
                        String aliasHint = aliasHintColumn(colName);

                        fields.add(new FieldRow(
                                tenantCode, appCode, objectCode,
                                fieldCode, humanize(colName), 
                                colName,aliasHint, dataTypeCode,
                                nullable != DatabaseMetaData.columnNullable,
                                remarks,
                                objectCode + " " + fieldCode + " " + colName + " " + colName
                        ));
                    }
                }
            }

            // batch insert fields
            batchInsertFields(fields);
            stats.fields += fields.size();

            // 3) FK -> relations + pending join keys
            for (String tableKey : objectCodeByTableKey.keySet()) {
                String[] parts = tableKey.split("\\.", 2);
                String schema = parts[0];
                String table  = parts[1];

                try (ResultSet fk = md.getImportedKeys(cx.getCatalog(), schema, table)) {
                    Map<String, List<FkRow>> byFk = new LinkedHashMap<>();
                    while (fk.next()) {
                        String fkName = fk.getString("FK_NAME");
                        String pkSchema = fk.getString("PKTABLE_SCHEM");
                        String pkTable  = fk.getString("PKTABLE_NAME");
                        String pkCol    = fk.getString("PKCOLUMN_NAME");
                        String fkSchema = fk.getString("FKTABLE_SCHEM");
                        String fkTable  = fk.getString("FKTABLE_NAME");
                        String fkCol    = fk.getString("FKCOLUMN_NAME");
                        short keySeq    = fk.getShort("KEY_SEQ");

                        if (fkName == null) fkName = "FK_" + fkSchema + "_" + fkTable + "_" + keySeq;

                        byFk.computeIfAbsent(fkName, k -> new ArrayList<>())
                                .add(new FkRow(pkSchema, pkTable, pkCol, fkSchema, fkTable, fkCol, keySeq));
                    }

                    for (var e : byFk.entrySet()) {
                        String fkName = e.getKey();
                        List<FkRow> rows = e.getValue();
                        rows.sort(Comparator.comparingInt(r -> r.keySeq));

                        FkRow first = rows.get(0);
                        String fromKey = first.fkSchema + "." + first.fkTable;
                        String toKey   = first.pkSchema + "." + first.pkTable;

                        String fromObject = objectCodeByTableKey.get(fromKey);
                        String toObject   = objectCodeByTableKey.get(toKey);
                        if (fromObject == null || toObject == null) continue;

                        String relCode = ("REL_" + fromObject + "_" + toObject + "_" + fkName)
                                .toUpperCase().replaceAll("[^A-Z0-9_]", "_");

                        // MANY_TO_ONE: FK table -> PK table (Employee -> Department)
                        relations.add(new RelationRow(conn.id(), tenantCode, appCode, 
                            relCode, fromObject, toObject, "MANY_TO_ONE"));

                        // defer join keys until we have relation_id
                        pendingJoinKeys.add(PendingJoinKey.of(relCode, rows));
                        
                        // ONE_TO_MANY: reverse relation (Department -> Employee)
                        String reverseRelCode = ("REL_" + toObject + "_" + fromObject + "_REV_" + fkName)
                                .toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                        
                        relations.add(new RelationRow(conn.id(), tenantCode, appCode,
                            reverseRelCode, toObject, fromObject, "ONE_TO_MANY"));
                        
                        // Reverse join keys (swap from/to)
                        List<FkRow> reverseRows = new ArrayList<>();
                        for (FkRow row : rows) {
                            reverseRows.add(new FkRow(
                                row.fkSchema, row.fkTable, row.fkCol,  // reversed: FK becomes "PK"
                                row.pkSchema, row.pkTable, row.pkCol,  // reversed: PK becomes "FK"
                                row.keySeq
                            ));
                        }
                        pendingJoinKeys.add(PendingJoinKey.of(reverseRelCode, reverseRows));
                    }
                }
            }

            // phase A: batch insert relations (no RETURNING)
            batchInsertRelations(relations);
            stats.relations += relations.size();

            // phase B: query id map by code
            Map<String, Integer> relIdByCode = fetchRelationIdsByCodes(tenantCode, appCode, conn.id(),
                    relations.stream().map(RelationRow::code).toList());

            // phase C: build join keys rows -> batch insert
            List<JoinKeyRow> joinKeys = new ArrayList<>();
            for (PendingJoinKey pj : pendingJoinKeys) {
                Integer relId = relIdByCode.get(pj.relCode);
                if (relId == null) continue;

                int seq = 1;
                for (FkRow r : pj.rows) {
                    joinKeys.add(new JoinKeyRow(
                            relId, seq++,
                            r.fkCol, "=", r.pkCol,
                            false,
                            conn.id(), tenantCode, appCode
                    ));
                }
            }

            batchInsertJoinKeys(joinKeys);
            stats.joinKeys += joinKeys.size();
        }
    }

    // ================
    // Pending join keys
    // ================
    private static class PendingJoinKey {
        final String relCode;
        final List<FkRow> rows;
        private PendingJoinKey(String relCode, List<FkRow> rows) {
            this.relCode = relCode;
            this.rows = rows;
        }
        static PendingJoinKey of(String relCode, List<FkRow> rows) {
            return new PendingJoinKey(relCode, rows);
        }
    }

    // =========================
    // Batch insert implementations
    // =========================
    private void batchInsertObjects(List<ObjectRow> rows) {
        if (rows.isEmpty()) return;

        final String sql = """
            INSERT INTO dqes.qrytb_object_meta
              (object_code, object_name, db_table, alias_hint, dbconn_id, description,
               fts_string_value, tenant_code, app_code)
            VALUES
              (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        batch(sql, rows, (ps, r) -> {
            ps.setString(1, r.objectCode());
            ps.setString(2, r.objectName());
            ps.setString(3, r.dbTable());
            ps.setString(4, r.aliasHint());
            ps.setInt(5, r.dbconnId());
            ps.setString(6, r.description());
            ps.setString(7, r.fts());
            ps.setString(8, r.tenant());
            ps.setString(9, r.app());
        });
    }

    private void batchInsertFields(List<FieldRow> rows) {
        if (rows.isEmpty()) return;

        final String sql = """
            INSERT INTO dqes.qrytb_field_meta
              (object_code, field_code, field_label, alias_hint, mapping_type, column_name,
               data_type, not_null, allow_select, allow_filter, allow_sort,
               description, fts_string_value, tenant_code, app_code)
            VALUES
              (?, ?, ?, ?, ?, ?, ?, ?, true, true, true, ?, ?, ?, ?)
            """;
        String mappingType = "COLUMN";
        batch(sql, rows, (ps, r) -> {
            ps.setString(1, r.objectCode());
            ps.setString(2, r.fieldCode());
            ps.setString(3, r.fieldLabel());
            ps.setString(4, r.aliasHint());
            ps.setString(5, mappingType);
            ps.setString(6, r.columnName());
            ps.setString(7, r.dataType());
            ps.setBoolean(8, r.notNull());
            ps.setString(9, r.description());
            ps.setString(10, r.fts());
            ps.setString(11, r.tenant());
            ps.setString(12, r.app());
        });
    }

    private void batchInsertRelations(List<RelationRow> rows) {
        if (rows.isEmpty()) return;

        final String sql = """
            INSERT INTO dqes.qrytb_relation_info
              (code, from_object_code, to_object_code, relation_type, join_type, filter_mode,
               path_weight, relation_props, dbconn_id, tenant_code, app_code)
            VALUES
              (?, ?, ?, ?, 'LEFT', 'AUTO',
               10, jsonb_build_object('dbconn_id', ?), ?, ?, ?)
            """;

        batch(sql, rows, (ps, r) -> {
            ps.setString(1, r.code());
            ps.setString(2, r.fromObject());
            ps.setString(3, r.toObject());
            ps.setString(4, r.relationType()); // Use relationType from record
            ps.setInt(5, r.dbconnId()); // for relation_props
            ps.setInt(6, r.dbconnId()); // dbconn_id column
            ps.setString(7, r.tenant());
            ps.setString(8, r.app());
        });
    }

    private Map<String, Integer> fetchRelationIdsByCodes(
        String tenant, String app, int dbconnId, List<String> codes) {

        if (codes == null || codes.isEmpty()) return Map.of();

        String sql = """
            SELECT code, id
            FROM dqes.qrytb_relation_info
            WHERE tenant_code=:tenant
            AND app_code=:app
            AND dbconn_id=:dbconnId
            AND code IN (:codes)
            """;

        Map<String, Object> p = new HashMap<>();
        p.put("tenant", tenant);
        p.put("app", app);
        p.put("dbconnId", dbconnId);
        p.put("codes", codes); // <-- List<String>

        Map<String, Integer> out = new HashMap<>();
        dqesJdbc.query(sql, p, (org.springframework.jdbc.core.RowCallbackHandler) rs -> 
            out.put(rs.getString("code"), rs.getInt("id")));
        return out;
    }


    private void batchInsertJoinKeys(List<JoinKeyRow> rows) {
        if (rows.isEmpty()) return;

        final String sql = """
            INSERT INTO dqes.qrytb_relation_join_key
              (relation_id, seq, from_column_name, operator, to_column_name, null_safe, dbconn_id,
               tenant_code, app_code)
            VALUES
              (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        batch(sql, rows, (ps, r) -> {
            ps.setInt(1, r.relationId());
            ps.setInt(2, r.seq());
            ps.setString(3, r.fromColumn());
            ps.setString(4, r.operator());
            ps.setString(5, r.toColumn());
            ps.setBoolean(6, r.nullSafe());
            ps.setInt(7, r.dbconnId());
            ps.setString(8, r.tenant());
            ps.setString(9, r.app());
        });
    }

    // =========================
    // Generic chunked batch helper
    // =========================
    private interface Binder<T> {
        void bind(java.sql.PreparedStatement ps, T row) throws SQLException;
    }

    private <T> void batch(String sql, List<T> rows, Binder<T> binder) {
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<T> chunk = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));

            dqesPlainJdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override public void setValues(java.sql.PreparedStatement ps, int idx) throws SQLException {
                    binder.bind(ps, chunk.get(idx));
                }
                @Override public int getBatchSize() { return chunk.size(); }
            });
        }
    }

    private String toObjectCode(String schema, String table) {
        return (schema + "_" + table).toUpperCase().replaceAll("[^A-Z0-9_]", "_");
    }

    private String toFieldCode(String col) {
        return col.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
    }

    private String mapToDqesDataType(int jdbcType, String typeNameRaw) {
        String typeName = typeNameRaw == null ? "" : typeNameRaw.toLowerCase();

        // Postgres-specific first
        if (typeName.equals("json") || typeName.equals("jsonb"))
            return "JSON";
        if (typeName.equals("tsvector"))
            return "TSVECTOR";
        if (typeName.equals("uuid"))
            return "UUID";
        if (typeName.equals("date"))
            return "DATE";
        if (typeName.contains("timestamp"))
            return "TIMESTAMP";
        if (typeName.equals("bool") || typeName.equals("boolean"))
            return "BOOLEAN";

        // Fallback by JDBC type
        return switch (jdbcType) {
            case java.sql.Types.BOOLEAN, java.sql.Types.BIT -> "BOOLEAN";
            case java.sql.Types.DATE -> "DATE";
            case java.sql.Types.TIMESTAMP, java.sql.Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP";
            case java.sql.Types.INTEGER, java.sql.Types.BIGINT, java.sql.Types.SMALLINT, java.sql.Types.TINYINT ->
                "INT";
            case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL, java.sql.Types.FLOAT, java.sql.Types.DOUBLE,
                    java.sql.Types.REAL ->
                "NUMBER";
            default -> "STRING";
        };
    }

    private static class FkRow {
        final String pkSchema, pkTable, pkCol;
        final String fkSchema, fkTable, fkCol;
        final int keySeq;

        FkRow(String pkSchema, String pkTable, String pkCol,
                String fkSchema, String fkTable, String fkCol,
                int keySeq) {
            this.pkSchema = pkSchema;
            this.pkTable = pkTable;
            this.pkCol = pkCol;
            this.fkSchema = fkSchema;
            this.fkTable = fkTable;
            this.fkCol = fkCol;
            this.keySeq = keySeq;
        }
    }

    private static String aliasHint(String s) {
        if (s == null) return "t";
        s = getFriendlyname(s);
        return getFieldName(s);
    }

    private static String aliasHintColumn(String s) {
        if (s == null) return "t";
        return getFieldName(s);
    }

    private static String getFriendlyname(String s) {
        if (s == null) return null;
        s = s.trim();

        int idx = s.indexOf('_');
        if (idx < 0) return s;                 // không có "_" thì giữ nguyên
        if (idx == s.length() - 1) return "";  // kết thúc bằng "_" thì ra rỗng

        return s.substring(idx + 1); // bỏ phần trước "_" đầu tiên
    }

    private static String getFieldName(String columnName) {
		return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, columnName);
	}

    private static String humanize(String s) {
        if (s == null) return "";
        String x = s.replace('_', ' ').trim();
        if (x.isEmpty()) return x;
        return Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }
}
