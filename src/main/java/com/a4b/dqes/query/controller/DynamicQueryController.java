package com.a4b.dqes.query.controller;

import com.a4b.dqes.query.dto.DynamicQueryRequest;
import com.a4b.dqes.query.dto.DynamicQueryResult;
import com.a4b.dqes.query.service.DynamicQueryExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Dynamic Query Engine
 * Provides endpoint for executing dynamic queries with multi-hop joins
 */
@RestController
@RequestMapping("/api/v1/dynamic-query")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dynamic Query", description = "Dynamic Query Engine API")
public class DynamicQueryController {
    
    private final DynamicQueryExecutionService queryExecutionService;
    
    @PostMapping("/execute")
    @Operation(
        summary = "Execute Dynamic Query",
        description = "Execute a dynamic query with support for multi-hop joins, " +
                     "EXISTS/NOT EXISTS operators, and Redis caching. " +
                     "The query engine automatically resolves relationships between objects " +
                     "and generates optimized SQL with named parameters."
    )
    public ResponseEntity<DynamicQueryResult> executeQuery(
        @Valid @RequestBody DynamicQueryRequest request
    ) {
        log.info("Executing dynamic query for tenant={}, app={}, root={}",
            request.getTenantCode(),
            request.getAppCode(),
            request.getRootObject()
        );
        
        try {
            DynamicQueryResult result = queryExecutionService.executeQuery(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid query request", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error executing query", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/health")
    @Operation(
        summary = "Health Check",
        description = "Check if the Dynamic Query Engine is operational"
    )
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Dynamic Query Engine is operational");
    }
}
