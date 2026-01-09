package com.a4b.dqes.web.rest;

import com.a4b.dqes.query.DynamicQueryExecutor;
import com.a4b.dqes.query.QueryRequest;
import com.a4b.dqes.query.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Dynamic Query Engine
 * 
 * Endpoints:
 * - POST /api/dqes/query/execute - Execute dynamic query
 * - POST /api/dqes/query/count - Get count only
 */
@Slf4j
@RestController
@RequestMapping("/api/dqes/query")
@RequiredArgsConstructor
@Tag(name = "Dynamic Query Engine", description = "Execute dynamic queries with multi-hop joins")
public class DynamicQueryResource {
    
    private final DynamicQueryExecutor queryExecutor;
    
    @PostMapping("/execute")
    @Operation(summary = "Execute dynamic query", 
               description = "Execute a dynamic query with SELECT, WHERE, ORDER BY, and multi-hop JOINs")
    public ResponseEntity<QueryResult> executeQuery(@RequestBody QueryRequest request) {
        log.info("POST /api/dqes/query/execute - root={}", request.getRootObjectCode());
        
        try {
            QueryResult result = queryExecutor.execute(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/count")
    @Operation(summary = "Get query count", 
               description = "Get count of rows matching the query criteria")
    public ResponseEntity<Long> executeCount(@RequestBody QueryRequest request) {
        log.info("POST /api/dqes/query/count - root={}", request.getRootObjectCode());
        
        try {
            long count = queryExecutor.executeCount(request);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("Count query failed", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
