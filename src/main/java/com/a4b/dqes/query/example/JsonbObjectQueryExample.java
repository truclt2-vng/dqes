package com.a4b.dqes.query.example;

import com.a4b.dqes.query.QueryRequest;
import com.a4b.dqes.query.QueryResult;
import com.a4b.dqes.query.DynamicQueryExecutor;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Examples demonstrating JSONB object grouping for joined tables
 * 
 * When selecting fields from joined objects, the engine now groups them
 * into JSONB objects for a cleaner, hierarchical result structure.
 * 
 * OLD BEHAVIOR (Flat columns):
 * {
 *   "code": "CODE1",
 *   "name": "Code 1",
 *   "name": "Group A"  // Duplicate column name!
 * }
 * 
 * NEW BEHAVIOR (Nested JSONB):
 * {
 *   "code": "CODE1",
 *   "name": "Code 1",
 *   "codeGroup": {      // Joined object as JSONB
 *     "name": "Group A",
 *     "description": "Group A Description"
 *   }
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonbObjectQueryExample {
    
    private final DynamicQueryExecutor executor;
    
    /**
     * Example 1: Simple join with JSONB grouping
     * 
     * Query: Select code info with its group as a JSONB object
     * 
     * OLD SQL (flat columns):
     * SELECT codeList.code AS code, codeList.name AS name, 
     *        codeGroup.name AS name, codeGroup.description AS description
     * 
     * NEW SQL (with JSONB grouping):
     * SELECT codeList.code AS code, codeList.name AS name,
     *        jsonb_build_object('name', codeGroup.name, 
     *                          'description', codeGroup.description) AS codeGroup
     */
    public void simpleJoinWithJsonbGrouping() {
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setObjectAlias("codeList");  // Root object
        
        // Simple string-based select fields
        request.setSelectFields(Arrays.asList(
            "codeList.code",
            "codeList.name",
            "codeGroup.name",
            "codeGroup.description"
        ));
        
        // Filter
        QueryRequest.Filter filter = new QueryRequest.Filter();
        filter.setField("codeList.record_status");
        filter.setOperatorCode("EQ");
        filter.setValue("O");
        
        request.setFilters(Arrays.asList(filter));
        request.setLimit(10);
        
        QueryResult result = executor.execute(request);
        
        log.info("Query returned {} rows", result.getRowCount());
        log.info("First row example:");
        if (!result.getRows().isEmpty()) {
            // Result structure:
            // {
            //   "code": "CODE1",
            //   "name": "Code 1",
            //   "codeGroup": {
            //     "name": "Group A",
            //     "description": "Group A Description"
            //   }
            // }
            log.info("{}", result.getRows().get(0));
        }
    }
    
    /**
     * Example 2: Multi-level joins with multiple JSONB objects
     * 
     * Query: Employee with Department and Manager as separate JSONB objects
     * 
     * Result structure:
     * {
     *   "id": 123,
     *   "name": "John Doe",
     *   "email": "john@example.com",
     *   "department": {
     *     "name": "Engineering",
     *     "code": "ENG"
     *   },
     *   "manager": {
     *     "name": "Jane Smith",
     *     "email": "jane@example.com"
     *   }
     * }
     */
    public void multiObjectJsonbGrouping() {
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setObjectAlias("emp");
        
        // Simple string-based select - much cleaner!
        request.setSelectFields(Arrays.asList(
            "emp.id",
            "emp.name",
            "emp.email",
            "dept.name",
            "dept.code",
            "mgr.name",
            "mgr.email"
        ));
        
        request.setLimit(10);
        
        QueryResult result = executor.execute(request);
        
        log.info("Multi-object query returned {} rows", result.getRowCount());
        log.info("Each row has nested dept and mgr JSONB objects");
    }
    
    /**
     * Example 3: Using alias-based syntax with JSONB grouping
     * 
     * JSON Request:
     * {
     *   "tenantCode": "SUPPER",
     *   "appCode": "SUPPER",
     *   "dbconnId": 1,
     *   "objectAlias": "codeList",
     *   "selectFields": [
     *     "codeList.code",
     *     "codeList.name",
     *     "codeGroup.name",
     *     "codeGroup.description"
     *   ],
     *   "filters": [
     *     {"field": "codeList.record_status", "operatorCode": "EQ", "value": "O"}
     *   ],
     *   "limit": 100
     * }
     * 
     * Generated SQL:
     * SELECT codeList.code AS code, 
     *        codeList.name AS name,
     *        jsonb_build_object('name', codeGroup.name, 
     *                          'description', codeGroup.description) AS codeGroup
     * FROM core.comtb_code_list codeList
     * LEFT JOIN core.comtb_code_group codeGroup ON codeList.group_id = codeGroup.id
     * WHERE codeList.record_status = :param0
     * LIMIT 100
     * 
     * Result:
     * [
     *   {
     *     "code": "CODE1",
     *     "name": "Code 1",
     *     "codeGroup": {
     *       "name": "Group A",
     *       "description": "Description A"
     *     }
     *   },
     *   ...
     * ]
     */
    public void aliasBasedWithJsonbGrouping() {
        log.info("See method comments for complete JSON request example");
    }
    
    /**
     * Benefits of JSONB grouping:
     * 
     * 1. NO COLUMN NAME CONFLICTS
     *    - Old: Two "name" columns (codeList.name and codeGroup.name)
     *    - New: Clear separation (root has "name", codeGroup JSONB has its own "name")
     * 
     * 2. HIERARCHICAL STRUCTURE
     *    - Results mirror the entity relationships
     *    - Easier to map to DTOs/domain objects
     * 
     * 3. CLEANER API RESPONSES
     *    - Frontend can easily access nested objects
     *    - No need to manually group flat columns
     * 
     * 4. TYPE SAFETY
     *    - JSONB preserves data types (numbers, booleans, nulls)
     *    - Can nest complex objects if needed
     * 
     * 5. EXTENSIBLE
     *    - Easy to add more fields to nested objects
     *    - Doesn't pollute root level namespace
     */
}
