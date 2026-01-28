package com.a4b.dqes.query.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.a4b.core.server.json.JSON;
import com.a4b.dqes.datasource.DynamicDataSourceService;
import com.a4b.dqes.domain.QrytbFieldMeta;
import com.a4b.dqes.domain.QrytbObjectMeta;
import com.a4b.dqes.domain.QrytbRelationInfo;
import com.a4b.dqes.domain.QrytbRelationJoinCondition;
import com.a4b.dqes.domain.QrytbRelationJoinKey;
import com.a4b.dqes.dto.schemacache.DbConnInfoDto;
import com.a4b.dqes.dto.schemacache.DbSchemaCacheRc;
import com.a4b.dqes.dto.schemacache.ObjectMetaRC;
import com.a4b.dqes.dto.schemacache.RelationInfoRC;
import com.a4b.dqes.exception.DqesRuntimeException;
import com.a4b.dqes.query.builder.PostgreQueryBuilder;
import com.a4b.dqes.query.builder.SqlQuery;
import com.a4b.dqes.query.dto.DynamicQueryRequest;
import com.a4b.dqes.query.dto.DynamicQueryResult;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.planner.DotPath;
import com.a4b.dqes.query.planner.FieldExtractor;
import com.a4b.dqes.query.planner.FieldKey;
import com.a4b.dqes.query.planner.JoinPathPlanner;
import com.a4b.dqes.query.planner.PlanRequest;
import com.a4b.dqes.query.planner.Planner;
import com.a4b.dqes.repository.jpa.FieldMetaJpaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaJpaRepository;
import com.a4b.dqes.repository.jpa.QrytbRelationJoinConditionJpaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;
import com.a4b.dqes.repository.jpa.RelationJoinKeyRepository;
import com.a4b.dqes.service.CfgtbDbconnInfoService;
import com.google.common.base.CaseFormat;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicQueryEngineService {

    private final DbSchemaCacheService dbSchemaCacheService;

    private final ObjectMetaJpaRepository objectMetaRepository;
    private final FieldMetaJpaRepository fieldMetaRepository;
    private final RelationInfoRepository relationInfoRepository;
    private final RelationJoinKeyRepository relationJoinKeyRepository;
    private final QrytbRelationJoinConditionJpaRepository relationJoinConditionRepository;
    private final CfgtbDbconnInfoService dbconnInfoService;
    
    private final Map<String, String> columnNameCache = new ConcurrentHashMap<>();

    private final JoinPathPlanner joinPathPlanner;
    private final PostgreQueryBuilder queryBuilder;
    private final DynamicDataSourceService dynamicDataSourceService;

    // Virtual threads (Java 21+) fallback fixed pool
    private final ExecutorService executorService = createExecutorService();

    private static ExecutorService createExecutorService() {
        try {
            return Executors.newVirtualThreadPerTaskExecutor();
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // static log (từ Lombok) dùng được trong static method
            log.warn("Virtual threads not available, using fixed thread pool");
            return Executors.newFixedThreadPool(10);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    @Transactional(readOnly = true)
    public DynamicQueryResult executeQuery(DynamicQueryRequest request,String connCode) {
        final long startTime = System.currentTimeMillis();

        try {
            DbConnInfoDto dbconnInfoDto = dbconnInfoService.getDbConnInfoByCode(connCode);
            final Integer dbconnId = dbconnInfoDto.getId().intValue();

            DbSchemaCacheRc dbSchemaCacheRc = dbSchemaCacheService.loadDbSchemaCache(connCode);

            List<ObjectMetaRC> objectMetas = dbSchemaCacheRc.getObjectMetas();
            List<RelationInfoRC> relationInfos = dbSchemaCacheRc.getRelationInfos();

            Map<String, ObjectMetaRC> allObjectMetaMap = objectMetas.stream()
                .collect(Collectors.toMap(ObjectMetaRC::getObjectCode, om -> om));
            
            final ObjectMetaRC rootObject = allObjectMetaMap.get(request.getRootObject());
            if (rootObject == null) {
                throw new DqesRuntimeException("Root object not found: " + request.getRootObject());
            }

            // Parse dot-paths
            List<FieldKey> selectFields = FieldExtractor.extractSelectFields(request.getSelectFields());

            Set<String> filterFieldkeys = FieldExtractor.extractFilterFields(request.getFilters());
            List<FieldKey> filterFields = filterFieldkeys.stream().map(DotPath::parse).toList();

            // Objects
            Set<String> selectObjects = selectFields.stream().map(FieldKey::objectCode).collect(Collectors.toSet());
            Set<String> filterObjects = filterFields.stream().map(FieldKey::objectCode).collect(Collectors.toSet());

            Set<String> requiredObjects = new HashSet<>();
            requiredObjects.add(rootObject.getObjectCode());
            requiredObjects.addAll(selectObjects);
            requiredObjects.addAll(filterObjects);

            PlanRequest planReq = new PlanRequest(dbconnId, rootObject, selectFields, filterFields);

            final QueryContext context = QueryContext.builder()
                .dbconnId(request.getDbconnId())
                .rootObject(request.getRootObject())
                .rootTable(rootObject.getDbTable())
                .allObjectMetaMap(allObjectMetaMap)
                .allRelationInfos(relationInfos)
                .selectFields(selectFields)
                .filterFields(filterFields)
                .distinct(request.getDistinct())
                .countOnly(request.getCountOnly())
                .offset(request.getOffset())
                .limit(request.getLimit())
                .build();

            Planner planner = joinPathPlanner.plan(planReq, context);
            log.info("Planning completed in {} ms", System.currentTimeMillis() - startTime);
            
            Long totalCount = null;
            List<Map<String, Object>> data = null;

            String executedSql;
            Map<String, Object> executedParams;

            Integer offset = null;
            Integer limit = null;


            SqlQuery query = queryBuilder.buildQuery(context, planner, planReq, request.getFilters(), request.getSorts());
            executedSql = query.getSql();
            executedParams = query.getParameters();

            final long planningTime = System.currentTimeMillis() - startTime;

            final NamedParameterJdbcTemplate targetJdbcTemplate = dynamicDataSourceService.getJdbcTemplate(connCode);
            final boolean countOnly = Boolean.TRUE.equals(request.getCountOnly());
            if (countOnly) {
                totalCount = targetJdbcTemplate.queryForObject(executedSql, executedParams, Long.class);
            }else{
                data = targetJdbcTemplate.queryForList(executedSql, executedParams);
                offset = request.getOffset();
                limit = request.getLimit();
            }

            final long executionTime = System.currentTimeMillis() - startTime;
            return DynamicQueryResult.builder()
                .data(normalizeData(data))
                .totalCount(totalCount)
                .offset(offset)
                .limit(limit)
                .executedSql(executedSql)
                .parameters(executedParams)
                .executionTimeMs(executionTime)
                .planningTimeMs(planningTime)
                .build();
        } catch (Exception e) {
            throw (e instanceof DqesRuntimeException dre)
                ? dre
                : new DqesRuntimeException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> normalizeData(List<Map<String, Object>> data) {
        if (data == null) return null;

        final List<T> newData = new ArrayList<>(data.size());

        for (Map<String, Object> item : data) {
            final Map<String, Object> dt = new HashMap<>(item.size());
            for (Entry<String, Object> entry : item.entrySet()) {
                final String fieldName = toCamelCached(entry.getKey());
                final Object v = entry.getValue();
                if (v instanceof PGobject pg) {
                    dt.put(fieldName, getPgObjectValue(pg));
                } else {
                    dt.put(fieldName, v);
                }
            }
            newData.add((T) dt);
        }
        return newData;
    }

    private String toCamelCached(String columnName) {
        if (columnName == null) return null;
        return columnNameCache.computeIfAbsent(columnName,
            k -> CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, k)
        );
    }

    private Object getPgObjectValue(PGobject val) {
        final String type = val.getType();
        final String raw = val.getValue();

        if (raw == null) return null;

        if ("jsonb".equals(type) || "json".equals(type)) {
            try {
                final String json = raw.trim();
                if (json.startsWith("[")) {
                    return JSON.getObjectMapper().readValue(json, List.class);
                }
                if (json.startsWith("{")) {
                    return JSON.getObjectMapper().readValue(json, Map.class);
                }
                return json;
            } catch (Exception e) {
                throw new DqesRuntimeException("Failed to read PgObject value", e);
            }
        }

        if ("ltree".equals(type)) {
            return raw;
        }

        // trả về raw thay vì trả về cả PGobject (thường UI muốn value)
        return raw;
    }
}
