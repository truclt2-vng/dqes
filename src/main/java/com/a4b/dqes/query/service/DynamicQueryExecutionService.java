package com.a4b.dqes.query.service;

import com.a4b.dqes.datasource.DynamicDataSourceService;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.query.builder.SqlQueryBuilder;
import com.a4b.dqes.query.dto.DynamicQueryRequest;
import com.a4b.dqes.query.dto.DynamicQueryResult;
import com.a4b.dqes.query.dto.FilterCriteria;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.model.ResolvedField;
import com.a4b.dqes.repository.jpa.ObjectMetaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main service for executing dynamic queries with performance optimizations
 * Coordinates field resolution, SQL building, and query execution
 * Features: Async count query, batch field resolution, optimized caching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicQueryExecutionService {
    
    private final ObjectMetaRepository objectMetaRepository;
    private final FieldResolverService fieldResolverService;
    private final SqlQueryBuilder sqlQueryBuilder;
    private final DynamicDataSourceService dynamicDataSourceService;
    
    // Java 21+ Virtual Threads for optimal async performance
    // Gracefully degrades to fixed thread pool if virtual threads unavailable
    private final ExecutorService executorService = createExecutorService();
    
    private static ExecutorService createExecutorService() {
        try {
            // Try to use Virtual Threads (Java 21+)
            return Executors.newVirtualThreadPerTaskExecutor();
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // Fallback to fixed thread pool for Java <21
            log.warn("Virtual threads not available, using fixed thread pool");
            return Executors.newFixedThreadPool(10);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
    
    /**
     * Execute a dynamic query with caching support and performance optimizations
     * Features: Async count query, parallel field resolution, batch query optimization
     */
    @Cacheable(value = "queryResults", 
               key = "#request.tenantCode + '_' + #request.appCode + '_' + #request.hashCode()",
               unless = "#result == null || #result.data.isEmpty()")
    @Transactional(readOnly = true)
    public DynamicQueryResult executeQuery(DynamicQueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate root object exists
            ObjectMeta rootObject = objectMetaRepository
                .findByTenantCodeAndAppCodeAndObjectCode(
                    request.getTenantCode(),
                    request.getAppCode(),
                    request.getRootObject()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                    "Root object not found: " + request.getRootObject()
                ));
            
            // Initialize query context
            QueryContext context = QueryContext.builder()
                .tenantCode(request.getTenantCode())
                .appCode(request.getAppCode())
                .dbconnId(request.getDbconnId())
                .rootObject(request.getRootObject())
                .rootTable(rootObject.getDbTable())
                .build();
            
            // Generate alias for root object
            context.getOrGenerateAlias(request.getRootObject(), rootObject.getAliasHint());

            context.getObjectTables().put(request.getRootObject(), rootObject.getDbTable());
            
            // Resolve select fields in parallel for better performance
            List<ResolvedField> resolvedFields = new ArrayList<>();
            if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
                List<ResolvedField> resolvedFieldsResp = fieldResolverService.batchResolveFields(
                    request.getSelectFields(), 
                    context
                );
                resolvedFields.addAll(resolvedFieldsResp);
            }
            
            // Resolve filter fields
            if (request.getFilters() != null) {
                resolveFilterFields(request.getFilters(), context);
            }
            
            // Build SQL query
            SqlQueryBuilder.SqlQuery sqlQuery = sqlQueryBuilder.buildQuery(
                context,
                resolvedFields,
                request.getFilters(),
                request.getOffset(),
                request.getLimit(),
                false
            );
            
            // Get the appropriate JDBC template for the target database
            NamedParameterJdbcTemplate targetJdbcTemplate = dynamicDataSourceService.getJdbcTemplate(
                request.getTenantCode(),
                request.getAppCode(),
                request.getDbconnId()
            );
            
            // Execute main query and count query in parallel using virtual threads
            CompletableFuture<List<Map<String, Object>>> dataFuture = 
                CompletableFuture.supplyAsync(() -> 
                    targetJdbcTemplate.queryForList(
                        sqlQuery.getSql(),
                        sqlQuery.getParameters()
                    ), executorService
                );
            
            CompletableFuture<Long> countFuture = null;
            if (!request.getCountOnly() && request.getLimit() != null) {
                countFuture = CompletableFuture.supplyAsync(() -> {
                    SqlQueryBuilder.SqlQuery countQuery = sqlQueryBuilder.buildQuery(
                        context,
                        resolvedFields,
                        request.getFilters(),
                        null,
                        null,
                        true
                    );
                    
                    return targetJdbcTemplate.queryForObject(
                        countQuery.getSql(),
                        countQuery.getParameters(),
                        Long.class
                    );
                }, executorService);
            }
            
            // Wait for results
            List<Map<String, Object>> data = dataFuture.join();
            Long totalCount = (countFuture != null) ? countFuture.join() : null;
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("Query executed in {}ms, returned {} rows{}", 
                executionTime, 
                data.size(),
                totalCount != null ? ", total: " + totalCount : ""
            );
            
            return DynamicQueryResult.builder()
                .data(data)
                .totalCount(totalCount)
                .offset(request.getOffset())
                .limit(request.getLimit())
                .executedSql(sqlQuery.getSql())
                .parameters(sqlQuery.getParameters())
                .executionTimeMs(executionTime)
                .build();
                
        } catch (Exception e) {
            log.error("Error executing dynamic query for {}.{}", 
                request.getTenantCode(), request.getRootObject(), e);
            throw new RuntimeException("Failed to execute query: " + e.getMessage(), e);
        }
    }
    
    /**
     * Resolve all fields referenced in filters
     */
    private void resolveFilterFields(List<FilterCriteria> filters, QueryContext context) {
        for (FilterCriteria filter : filters) {
            if (filter.getField() != null) {
                fieldResolverService.resolveField(filter.getField(), context);
            }
            if (filter.getSubFilters() != null) {
                resolveFilterFields(filter.getSubFilters(), context);
            }
        }
    }
}
