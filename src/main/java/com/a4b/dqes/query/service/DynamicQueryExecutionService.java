package com.a4b.dqes.query.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
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
import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.exception.DqesRuntimeException;
import com.a4b.dqes.query.builder.SqlQueryBuilder;
import com.a4b.dqes.query.dto.DynamicQueryRequest;
import com.a4b.dqes.query.dto.DynamicQueryResult;
import com.a4b.dqes.query.dto.FilterCriteria;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.model.ResolvedField;
import com.a4b.dqes.repository.jpa.FieldMetaRepository;
import com.a4b.dqes.repository.jpa.ObjectMetaRepository;
import com.google.common.base.CaseFormat;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicQueryExecutionService {

    private final ObjectMetaRepository objectMetaRepository;
    private final FieldMetaRepository fieldMetaRepository;
    private final FieldResolverService fieldResolverService;
    private final SqlQueryBuilder sqlQueryBuilder;
    private final DynamicDataSourceService dynamicDataSourceService;

    // cache snake_case -> camelCase theo columnName (tối ưu normalizeData cho nhiều rows)
    private final Map<String, String> columnNameCache = new ConcurrentHashMap<>();

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
    public DynamicQueryResult executeQuery(DynamicQueryRequest request) {
        final long startTime = System.currentTimeMillis();

        try {
            final String tenant = request.getTenantCode();
            final String app = request.getAppCode();

            // Load meta 1 lần
            final List<ObjectMeta> objectMetas = objectMetaRepository.findByTenantCodeAndAppCode(tenant, app);
            final List<FieldMeta> allFieldMetas = fieldMetaRepository.findByTenantCodeAndAppCode(tenant, app);

            final Map<String, List<FieldMeta>> fieldMetaByObject =
                allFieldMetas.stream().collect(Collectors.groupingBy(FieldMeta::getObjectCode));

            // Build object map + attach fieldMetas
            final Map<String, ObjectMeta> allObjectMetaMap = objectMetas.stream()
                .peek(om -> om.setFieldMetas(fieldMetaByObject.getOrDefault(om.getObjectCode(), List.of())))
                .collect(Collectors.toMap(ObjectMeta::getObjectCode, om -> om, (a, b) -> a, HashMap::new));

            final ObjectMeta rootObject = allObjectMetaMap.get(request.getRootObject());
            if (rootObject == null) {
                throw new DqesRuntimeException("Root object not found: " + request.getRootObject());
            }

            final QueryContext context = QueryContext.builder()
                .tenantCode(tenant)
                .appCode(app)
                .dbconnId(request.getDbconnId())
                .rootObject(request.getRootObject())
                .rootTable(rootObject.getDbTable())
                .allObjectMetaMap(allObjectMetaMap)
                .build();

            // root alias
            context.getOrGenerateAlias(rootObject.getObjectCode(), rootObject.getAliasHint());

            // tables map (nếu QueryBuilder cần)
            allObjectMetaMap.forEach((objectCode, objectMeta) ->
                context.getObjectTables().put(objectCode, objectMeta.getDbTable())
            );

            // Resolve select fields (batch)
            final List<ResolvedField> resolvedFields;
            if (request.getSelectFields() == null || request.getSelectFields().isEmpty()) {
                resolvedFields = List.of();
            } else {
                resolvedFields = fieldResolverService.batchResolveFields(
                    request.getSelectFields(),
                    context,
                    allObjectMetaMap
                );
            }

            // Resolve filter fields (đảm bảo join planner biết)
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                resolveFilterFields(request.getFilters(), context);
            }

            final NamedParameterJdbcTemplate targetJdbcTemplate = dynamicDataSourceService.getJdbcTemplate(
                tenant, app, request.getDbconnId()
            );

            final boolean countOnly = Boolean.TRUE.equals(request.getCountOnly());

            SqlQueryBuilder.SqlQuery dataQuery = null;
            SqlQueryBuilder.SqlQuery countQuery = null;

            CompletableFuture<List<Map<String, Object>>> dataFuture = null;
            CompletableFuture<Long> countFuture = null;
            Integer offset = null;
            Integer limit = null;

            if (countOnly) {
                countQuery = sqlQueryBuilder.buildQuery(
                    context,
                    resolvedFields,
                    request.getFilters(),
                    null,
                    null,
                    true
                );

                final SqlQueryBuilder.SqlQuery cq = countQuery;
                countFuture = CompletableFuture.supplyAsync(
                    () -> targetJdbcTemplate.queryForObject(cq.getSql(), cq.getParameters(), Long.class),
                    executorService
                );
            } else {
                offset = request.getOffset();
                limit = request.getLimit();
                dataQuery = sqlQueryBuilder.buildQuery(
                    context,
                    resolvedFields,
                    request.getFilters(),
                    request.getOffset(),
                    request.getLimit(),
                    false
                );

                final SqlQueryBuilder.SqlQuery dq = dataQuery;
                dataFuture = CompletableFuture.supplyAsync(
                    () -> targetJdbcTemplate.queryForList(dq.getSql(), dq.getParameters()),
                    executorService
                );
            }

            final List<Map<String, Object>> data = (dataFuture != null) ? dataFuture.join() : List.of();
            final Long totalCount = (countFuture != null) ? countFuture.join() : null;

            final long executionTime = System.currentTimeMillis() - startTime;

            // executedSql + params: set đúng cái query đã chạy
            final String executedSql = (countOnly ? countQuery : dataQuery).getSql();
            final Map<String, Object> executedParams = (countOnly ? countQuery : dataQuery).getParameters();

            log.info("Query executed in {}ms, returned {} rows{}",
                executionTime,
                data.size(),
                totalCount != null ? ", total: " + totalCount : ""
            );

            return DynamicQueryResult.builder()
                .data(normalizeData(data))
                .totalCount(totalCount)
                .offset(offset)
                .limit(limit)
                .executedSql(executedSql)
                .parameters(executedParams)
                .executionTimeMs(executionTime)
                .build();

        } catch (Exception e) {
            log.error("Error executing dynamic query for {}.{}",
                request.getTenantCode(), request.getRootObject(), e);
            throw (e instanceof DqesRuntimeException dre)
                ? dre
                : new DqesRuntimeException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    private void resolveFilterFields(List<FilterCriteria> filters, QueryContext context) {
        for (FilterCriteria filter : filters) {
            final String field = filter.getField();
            if (field != null && !field.isBlank()) {
                fieldResolverService.resolveField(field, context);
            }
            final List<FilterCriteria> sub = filter.getSubFilters();
            if (sub != null && !sub.isEmpty()) {
                resolveFilterFields(sub, context);
            }
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
