package com.a4b.dqes.query.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.a4b.dqes.query.dto.DynamicQueryRequest;
import com.a4b.dqes.query.dto.DynamicQueryResult;
import com.a4b.dqes.query.service.DynamicQueryEngineService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for Dynamic Query Engine
 * Provides endpoint for executing dynamic queries with multi-hop joins
 */
@RestController
@RequestMapping("/api/v1/dynamic")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dynamic Query", description = "Dynamic Query Engine API")
public class DynamicQueryController {
    
    private final DynamicQueryEngineService dynamicQueryEngineService;
    
    @PostMapping("/query/{datasource}/{objectCode}")
    @Operation(
        summary = "Execute Dynamic Query",
        description = "Execute a dynamic query with support for multi-hop joins and Redis caching." +
                     "The query engine automatically resolves relationships between objects " +
                     "and generates optimized SQL with named parameters."
    )
    public ResponseEntity<DynamicQueryResult> query(
        @PathVariable("datasource") String datasource,
        @PathVariable("objectCode") String objectCode,
        @Valid @RequestBody DynamicQueryRequest request
    ) {
        log.info("Executing dynamic query for root={}",request.getRootObject());
        
        try {
            request.setRootObject(objectCode);
            DynamicQueryResult result = dynamicQueryEngineService.executeQuery(request,datasource);
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
