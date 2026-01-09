package com.a4b.dqes.query.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for dynamic query metadata
 * Loads metadata from dqes schema with caching
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DqesMetadataRepository {
    
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    // ========== ObjectMeta ==========
    
    @Cacheable(value = "dqes-object-meta", key = "#tenantCode + '_' + #appCode + '_' + #objectCode")
    public Optional<ObjectMeta> findObjectMeta(String tenantCode, String appCode, String objectCode) {
        String sql = """
            SELECT id, tenant_code, app_code, object_code, object_name, 
                   db_table, alias_hint, dbconn_id, description, current_flg
            FROM dqes.qrytb_object_meta
            WHERE tenant_code = :tenantCode
              AND app_code = :appCode
              AND object_code = :objectCode
              AND current_flg = true
              AND record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("objectCode", objectCode);
        
        List<ObjectMeta> results = jdbcTemplate.query(sql, params, new ObjectMetaRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    @Cacheable(value = "dqes-object-meta-list", key = "#tenantCode + '_' + #appCode + '_' + #dbconnId")
    public List<ObjectMeta> findAllObjectMeta(String tenantCode, String appCode, Integer dbconnId) {
        String sql = """
            SELECT id, tenant_code, app_code, object_code, object_name, 
                   db_table, alias_hint, dbconn_id, description, current_flg
            FROM dqes.qrytb_object_meta
            WHERE tenant_code = :tenantCode
              AND app_code = :appCode
              AND dbconn_id = :dbconnId
              AND current_flg = true
              AND record_status <> 'D'
            ORDER BY object_code
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("dbconnId", dbconnId);
        
        return jdbcTemplate.query(sql, params, new ObjectMetaRowMapper());
    }
    
    // ========== RelationMeta ==========
    
    @Cacheable(value = "dqes-relation-meta", key = "#tenantCode + '_' + #appCode + '_' + #relationCode")
    public Optional<RelationMeta> findRelationMeta(String tenantCode, String appCode, String relationCode) {
        String sql = """
            SELECT r.id, r.tenant_code, r.app_code, r.code, r.from_object_code, r.to_object_code,
                   r.relation_type, r.join_type, r.filter_mode, r.is_required, r.is_navigable,
                   r.path_weight, r.depends_on_code, r.dbconn_id
            FROM dqes.qrytb_relation_info r
            WHERE r.tenant_code = :tenantCode
              AND r.app_code = :appCode
              AND r.code = :relationCode
              AND r.current_flg = true
              AND r.record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("relationCode", relationCode);
        
        List<RelationMeta> results = jdbcTemplate.query(sql, params, new RelationMetaRowMapper());
        
        if (results.isEmpty()) {
            return Optional.empty();
        }
        
        RelationMeta meta = results.get(0);
        meta.setJoinKeys(findJoinKeys(meta.getId()));
        
        return Optional.of(meta);
    }
    
    @Cacheable(value = "dqes-relation-meta-from", key = "#tenantCode + '_' + #appCode + '_' + #fromObjectCode")
    public List<RelationMeta> findRelationsFrom(String tenantCode, String appCode, String fromObjectCode) {
        String sql = """
            SELECT r.id, r.tenant_code, r.app_code, r.code, r.from_object_code, r.to_object_code,
                   r.relation_type, r.join_type, r.filter_mode, r.is_required, r.is_navigable,
                   r.path_weight, r.depends_on_code, r.dbconn_id
            FROM dqes.qrytb_relation_info r
            WHERE r.tenant_code = :tenantCode
              AND r.app_code = :appCode
              AND r.from_object_code = :fromObjectCode
              AND r.current_flg = true
              AND r.record_status <> 'D'
              AND r.is_navigable = true
            ORDER BY r.path_weight, r.code
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("fromObjectCode", fromObjectCode);
        
        List<RelationMeta> relations = jdbcTemplate.query(sql, params, new RelationMetaRowMapper());
        
        // Load join keys for each relation
        relations.forEach(meta -> meta.setJoinKeys(findJoinKeys(meta.getId())));
        
        return relations;
    }
    
    private List<RelationMeta.JoinKeyMeta> findJoinKeys(Integer relationId) {
        String sql = """
            SELECT id, relation_id, seq, from_column_name, operator, to_column_name, null_safe
            FROM dqes.qrytb_relation_join_key
            WHERE relation_id = :relationId
              AND current_flg = true
              AND record_status <> 'D'
            ORDER BY seq
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("relationId", relationId);
        
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            RelationMeta.JoinKeyMeta key = new RelationMeta.JoinKeyMeta();
            key.setId(rs.getInt("id"));
            key.setRelationId(rs.getInt("relation_id"));
            key.setSeq(rs.getInt("seq"));
            key.setFromColumnName(rs.getString("from_column_name"));
            key.setOperator(rs.getString("operator"));
            key.setToColumnName(rs.getString("to_column_name"));
            key.setNullSafe(rs.getBoolean("null_safe"));
            return key;
        });
    }
    
    // ========== FieldMeta ==========
    
    @Cacheable(value = "dqes-field-meta", key = "#tenantCode + '_' + #appCode + '_' + #objectCode + '_' + #fieldCode")
    public Optional<FieldMeta> findFieldMeta(String tenantCode, String appCode, String objectCode, String fieldCode) {
        String sql = """
            SELECT id, tenant_code, app_code, object_code, field_code, field_label, alias_hint,
                   mapping_type, column_name, select_expr_code, filter_expr_code, expr_args,
                   select_expr, filter_expr, expr_lang, data_type, not_null,
                   allow_select, allow_filter, allow_sort, default_select, description
            FROM dqes.qrytb_field_meta
            WHERE tenant_code = :tenantCode
              AND app_code = :appCode
              AND object_code = :objectCode
              AND field_code = :fieldCode
              AND current_flg = true
              AND record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("objectCode", objectCode)
            .addValue("fieldCode", fieldCode);
        
        List<FieldMeta> results = jdbcTemplate.query(sql, params, new FieldMetaRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    @Cacheable(value = "dqes-field-meta-list", key = "#tenantCode + '_' + #appCode + '_' + #objectCode")
    public List<FieldMeta> findFieldsByObject(String tenantCode, String appCode, String objectCode) {
        String sql = """
            SELECT id, tenant_code, app_code, object_code, field_code, field_label, alias_hint,
                   mapping_type, column_name, select_expr_code, filter_expr_code, expr_args,
                   select_expr, filter_expr, expr_lang, data_type, not_null,
                   allow_select, allow_filter, allow_sort, default_select, description
            FROM dqes.qrytb_field_meta
            WHERE tenant_code = :tenantCode
              AND app_code = :appCode
              AND object_code = :objectCode
              AND current_flg = true
              AND record_status <> 'D'
            ORDER BY field_code
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("objectCode", objectCode);
        
        return jdbcTemplate.query(sql, params, new FieldMetaRowMapper());
    }
    
    // ========== ObjectPathCache ==========
    
    @Cacheable(value = "dqes-object-path", key = "#tenantCode + '_' + #appCode + '_' + #fromObjectCode + '_' + #toObjectCode")
    public Optional<ObjectPathCache> findObjectPath(String tenantCode, String appCode, 
                                                     String fromObjectCode, String toObjectCode) {
        String sql = """
            SELECT id, tenant_code, app_code, from_object_code, to_object_code,
                   hop_count, total_weight, path_relation_codes, dbconn_id
            FROM dqes.qrytb_object_path_cache
            WHERE tenant_code = :tenantCode
              AND app_code = :appCode
              AND from_object_code = :fromObjectCode
              AND to_object_code = :toObjectCode
              AND current_flg = true
              AND record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("fromObjectCode", fromObjectCode)
            .addValue("toObjectCode", toObjectCode);
        
        List<ObjectPathCache> results = jdbcTemplate.query(sql, params, new ObjectPathCacheRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    // ========== ExprAllowlist ==========
    
    @Cacheable(value = "dqes-expr-allowlist", key = "#tenantCode + '_' + #appCode + '_' + #exprCode")
    public Optional<ExprAllowlist> findExprAllowlist(String tenantCode, String appCode, String exprCode) {
        String sql = """
            SELECT id, tenant_code, app_code, expr_code, expr_type, sql_template,
                   allow_in_select, allow_in_filter, allow_in_sort,
                   min_args, max_args, args_spec, return_data_type, description
            FROM dqes.qrytb_expr_allowlist
            WHERE tenant_code = :tenantCode
              AND app_code = :appCode
              AND expr_code = :exprCode
              AND current_flg = true
              AND record_status <> 'D'
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantCode", tenantCode)
            .addValue("appCode", appCode)
            .addValue("exprCode", exprCode);
        
        List<ExprAllowlist> results = jdbcTemplate.query(sql, params, new ExprAllowlistRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    // ========== Row Mappers ==========
    
    private static class ObjectMetaRowMapper implements RowMapper<ObjectMeta> {
        @Override
        public ObjectMeta mapRow(ResultSet rs, int rowNum) throws SQLException {
            ObjectMeta meta = new ObjectMeta();
            meta.setId(rs.getInt("id"));
            meta.setTenantCode(rs.getString("tenant_code"));
            meta.setAppCode(rs.getString("app_code"));
            meta.setObjectCode(rs.getString("object_code"));
            meta.setObjectName(rs.getString("object_name"));
            meta.setDbTable(rs.getString("db_table"));
            meta.setAliasHint(rs.getString("alias_hint"));
            meta.setDbconnId(rs.getInt("dbconn_id"));
            meta.setDescription(rs.getString("description"));
            meta.setCurrentFlg(rs.getBoolean("current_flg"));
            return meta;
        }
    }
    
    private static class RelationMetaRowMapper implements RowMapper<RelationMeta> {
        @Override
        public RelationMeta mapRow(ResultSet rs, int rowNum) throws SQLException {
            RelationMeta meta = new RelationMeta();
            meta.setId(rs.getInt("id"));
            meta.setTenantCode(rs.getString("tenant_code"));
            meta.setAppCode(rs.getString("app_code"));
            meta.setCode(rs.getString("code"));
            meta.setFromObjectCode(rs.getString("from_object_code"));
            meta.setToObjectCode(rs.getString("to_object_code"));
            meta.setRelationType(RelationMeta.RelationType.valueOf(rs.getString("relation_type")));
            meta.setJoinType(RelationMeta.JoinType.valueOf(rs.getString("join_type")));
            meta.setFilterMode(RelationMeta.FilterMode.valueOf(rs.getString("filter_mode")));
            meta.setIsRequired(rs.getBoolean("is_required"));
            meta.setIsNavigable(rs.getBoolean("is_navigable"));
            meta.setPathWeight(rs.getInt("path_weight"));
            meta.setDependsOnCode(rs.getString("depends_on_code"));
            meta.setDbconnId(rs.getInt("dbconn_id"));
            return meta;
        }
    }
    
    private class FieldMetaRowMapper implements RowMapper<FieldMeta> {
        @Override
        public FieldMeta mapRow(ResultSet rs, int rowNum) throws SQLException {
            FieldMeta meta = new FieldMeta();
            meta.setId(rs.getInt("id"));
            meta.setTenantCode(rs.getString("tenant_code"));
            meta.setAppCode(rs.getString("app_code"));
            meta.setObjectCode(rs.getString("object_code"));
            meta.setFieldCode(rs.getString("field_code"));
            meta.setFieldLabel(rs.getString("field_label"));
            meta.setAliasHint(rs.getString("alias_hint"));
            meta.setMappingType(FieldMeta.MappingType.valueOf(rs.getString("mapping_type")));
            meta.setColumnName(rs.getString("column_name"));
            meta.setSelectExprCode(rs.getString("select_expr_code"));
            meta.setFilterExprCode(rs.getString("filter_expr_code"));
            
            String exprArgsJson = rs.getString("expr_args");
            if (exprArgsJson != null) {
                try {
                    meta.setExprArgs(objectMapper.readTree(exprArgsJson));
                } catch (Exception e) {
                    log.warn("Failed to parse expr_args JSON: {}", exprArgsJson, e);
                }
            }
            
            meta.setSelectExpr(rs.getString("select_expr"));
            meta.setFilterExpr(rs.getString("filter_expr"));
            meta.setExprLang(rs.getString("expr_lang"));
            meta.setDataType(rs.getString("data_type"));
            meta.setNotNull(rs.getBoolean("not_null"));
            meta.setAllowSelect(rs.getBoolean("allow_select"));
            meta.setAllowFilter(rs.getBoolean("allow_filter"));
            meta.setAllowSort(rs.getBoolean("allow_sort"));
            meta.setDefaultSelect(rs.getBoolean("default_select"));
            meta.setDescription(rs.getString("description"));
            return meta;
        }
    }
    
    private class ObjectPathCacheRowMapper implements RowMapper<ObjectPathCache> {
        @Override
        public ObjectPathCache mapRow(ResultSet rs, int rowNum) throws SQLException {
            ObjectPathCache cache = new ObjectPathCache();
            cache.setId(rs.getInt("id"));
            cache.setTenantCode(rs.getString("tenant_code"));
            cache.setAppCode(rs.getString("app_code"));
            cache.setFromObjectCode(rs.getString("from_object_code"));
            cache.setToObjectCode(rs.getString("to_object_code"));
            cache.setHopCount(rs.getInt("hop_count"));
            cache.setTotalWeight(rs.getInt("total_weight"));
            cache.setDbconnId(rs.getInt("dbconn_id"));
            
            String pathJson = rs.getString("path_relation_codes");
            if (pathJson != null) {
                try {
                    List<String> pathCodes = objectMapper.readValue(pathJson, new TypeReference<List<String>>() {});
                    cache.setPathRelationCodes(pathCodes);
                } catch (Exception e) {
                    log.warn("Failed to parse path_relation_codes JSON: {}", pathJson, e);
                }
            }
            
            return cache;
        }
    }
    
    private class ExprAllowlistRowMapper implements RowMapper<ExprAllowlist> {
        @Override
        public ExprAllowlist mapRow(ResultSet rs, int rowNum) throws SQLException {
            ExprAllowlist meta = new ExprAllowlist();
            meta.setId(rs.getInt("id"));
            meta.setTenantCode(rs.getString("tenant_code"));
            meta.setAppCode(rs.getString("app_code"));
            meta.setExprCode(rs.getString("expr_code"));
            meta.setExprType(ExprAllowlist.ExprType.valueOf(rs.getString("expr_type")));
            meta.setSqlTemplate(rs.getString("sql_template"));
            meta.setAllowInSelect(rs.getBoolean("allow_in_select"));
            meta.setAllowInFilter(rs.getBoolean("allow_in_filter"));
            meta.setAllowInSort(rs.getBoolean("allow_in_sort"));
            meta.setMinArgs(rs.getInt("min_args"));
            meta.setMaxArgs(rs.getInt("max_args"));
            
            String argsSpecJson = rs.getString("args_spec");
            if (argsSpecJson != null) {
                try {
                    meta.setArgsSpec(objectMapper.readValue(argsSpecJson, Object.class));
                } catch (Exception e) {
                    log.warn("Failed to parse args_spec JSON: {}", argsSpecJson, e);
                }
            }
            
            meta.setReturnDataType(rs.getString("return_data_type"));
            meta.setDescription(rs.getString("description"));
            return meta;
        }
    }
}
