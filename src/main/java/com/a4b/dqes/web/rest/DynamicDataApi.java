package com.a4b.dqes.web.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.a4b.dqes.datasource.DynamicDataOperationService;
import com.a4b.dqes.dto.schemacache.DbSchemaCacheRc;
import com.a4b.dqes.query.service.DbSchemaCacheService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Dynamic Data Operations API
 * 
 * REST endpoints for dynamic CRUD operations on any table
 * using configured database connections
 */
@RestController
@RequestMapping("/api/dynamic-data")
@RequiredArgsConstructor
public class DynamicDataApi {
    
    private final DbSchemaCacheService dbSchemaCacheService;
    private final DynamicDataOperationService dataOperationService;

    /**
     * Insert a single row
     * POST /api/dynamic-data/{dbConnCode}/{tableName}
     * Body: { "column1": "value1", "column2": "value2" }
     */
    @PostMapping("/{dbConnCode}/{objectCode}")
    public ResponseEntity<InsertResponse> insert(
        @PathVariable String dbConnCode,
        @PathVariable String objectCode,
        @RequestBody Map<String, Object> data
    ) {
        DbSchemaCacheRc dbSchemaCacheRc = dbSchemaCacheService.loadDbSchemaCache(dbConnCode);
        InsertRequest request = new InsertRequest();
        request.setDbConnCode(dbConnCode);
        request.setObjectCode(objectCode);
        request.setData(data);
        request.setDbSchemaCacheRc(dbSchemaCacheRc);
        Map<String, Object> returnData = dataOperationService.insert(request);
        return ResponseEntity.ok(new InsertResponse(returnData, 1));
    }

    /**
     * Batch insert multiple rows
     * POST /api/dynamic-data/{dbConnCode}/{tableName}/batch
     * Body: [
     *   { "column1": "value1", "column2": "value2" },
     *   { "column1": "value3", "column2": "value4" }
     * ]
     */
    @PostMapping("/{dbConnCode}/{tableName}/batch")
    public ResponseEntity<InsertResponse> batchInsert(
        @PathVariable String dbConnCode,
        @PathVariable String tableName,
        @RequestBody List<Map<String, Object>> dataList
    ) {
        int count = dataOperationService.batchInsert(dbConnCode, tableName, dataList);
        return ResponseEntity.ok(new InsertResponse(null, count));
    }

    /**
     * Upsert (INSERT ... ON CONFLICT DO UPDATE)
     * POST /api/dynamic-data/{dbConnCode}/{tableName}/upsert
     * Body: {
     *   "data": { "id": 1, "name": "John", "email": "john@example.com" },
     *   "conflictColumns": ["id"]
     * }
     */
    @PostMapping("/{dbConnCode}/{tableName}/upsert")
    public ResponseEntity<InsertResponse> upsert(
        @PathVariable String dbConnCode,
        @PathVariable String tableName,
        @RequestBody UpsertRequest request
    ) {
        int count = dataOperationService.upsert(
            dbConnCode, tableName, request.getData(), request.getConflictColumns()
        );
        return ResponseEntity.ok(new InsertResponse(null, count));
    }

    /**
     * Update rows
     * PUT /api/dynamic-data/{dbConnCode}/{tableName}
     * Body: {
     *   "data": { "name": "Jane", "email": "jane@example.com" },
     *   "whereClause": "id = :id",
     *   "whereParams": { "id": 1 }
     * }
     */
    @PutMapping("/{dbConnCode}/{tableName}")
    public ResponseEntity<UpdateResponse> update(
        @PathVariable String dbConnCode,
        @PathVariable String tableName,
        @RequestBody UpdateRequest request
    ) {
        int count = dataOperationService.update(
            dbConnCode, tableName, request.getData(), 
            request.getWhereClause(), request.getWhereParams()
        );
        return ResponseEntity.ok(new UpdateResponse(count));
    }

    /**
     * Delete rows
     * DELETE /api/dynamic-data/{dbConnCode}/{tableName}
     * Body: {
     *   "whereClause": "id = :id",
     *   "whereParams": { "id": 1 }
     * }
     */
    @DeleteMapping("/{dbConnCode}/{tableName}")
    public ResponseEntity<DeleteResponse> delete(
        @PathVariable String dbConnCode,
        @PathVariable String tableName,
        @RequestBody DeleteRequest request
    ) {
        int count = dataOperationService.delete(
            dbConnCode, tableName, 
            request.getWhereClause(), request.getWhereParams()
        );
        return ResponseEntity.ok(new DeleteResponse(count));
    }

    /**
     * Query data
     * GET /api/dynamic-data/{dbConnCode}/query
     * Params: sql, param1, param2, etc.
     */
    @GetMapping("/{dbConnCode}/query")
    public ResponseEntity<List<Map<String, Object>>> query(
        @PathVariable String dbConnCode,
        @RequestParam String sql,
        @RequestParam(required = false) Map<String, Object> params
    ) {
        List<Map<String, Object>> results = dataOperationService.query(dbConnCode, sql, params);
        return ResponseEntity.ok(results);
    }

    /**
     * Count rows
     * GET /api/dynamic-data/{dbConnCode}/{tableName}/count
     * Params: whereClause, whereParams as query params
     */
    @GetMapping("/{dbConnCode}/{tableName}/count")
    public ResponseEntity<CountResponse> count(
        @PathVariable String dbConnCode,
        @PathVariable String tableName,
        @RequestParam(required = false) String whereClause,
        @RequestParam(required = false) Map<String, Object> whereParams
    ) {
        long count = dataOperationService.count(dbConnCode, tableName, whereClause, whereParams);
        return ResponseEntity.ok(new CountResponse(count));
    }

    @Data
    public static class InsertRequest {
        private String dbConnCode;
        private String objectCode;
        private Map<String, Object> data;
        private DbSchemaCacheRc dbSchemaCacheRc;
    }
    // DTOs
    @Data
    public static class InsertResponse {
        private final Map<String, Object> data;
        private final int rowsAffected;
    }

    @Data
    public static class UpdateResponse {
        private final int rowsAffected;
    }

    @Data
    public static class DeleteResponse {
        private final int rowsAffected;
    }

    @Data
    public static class CountResponse {
        private final long count;
    }

    @Data
    public static class UpsertRequest {
        private Map<String, Object> data;
        private List<String> conflictColumns;
    }

    @Data
    public static class UpdateRequest {
        private Map<String, Object> data;
        private String whereClause;
        private Map<String, Object> whereParams;
    }

    @Data
    public static class DeleteRequest {
        private String whereClause;
        private Map<String, Object> whereParams;
    }
}
