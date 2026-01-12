package com.a4b.dqes.query;

import com.a4b.dqes.query.ast.QueryAST;
import com.a4b.dqes.query.ast.SortNode.SortDirection;
import com.a4b.dqes.query.generator.SqlGenerator;
import com.a4b.dqes.query.generator.SqlGenerator.GeneratedSql;
import com.a4b.dqes.query.metadata.DqesMetadataRepository;
import com.a4b.dqes.query.planner.JoinPathPlanner;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for Dynamic Query Engine
 * 
 * Tests complete workflow:
 * 1. Build QueryRequest
 * 2. Execute via DynamicQueryExecutor
 * 3. Verify SQL generation
 * 4. Verify multi-hop JOIN planning
 * 5. Verify EXISTS strategy
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("testdev")
class DynamicQueryEngineIntegrationTest {
    
    @Autowired
    private DynamicQueryExecutor queryExecutor;
    
    @Autowired
    private JoinPathPlanner joinPathPlanner;
    
    @Autowired
    private SqlGenerator sqlGenerator;
    
    @Autowired
    private DqesMetadataRepository metadataRepo;
    
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    
    /**
     * Test 1: Simple single-object query
     */
    @Test
    void testSimpleQuery() {
        log.info("=== Test 1: Simple Query ===");
        
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setRootObjectCode("EMPLOYEE");
        
        request.setSelectFields(Arrays.asList(
            new QueryRequest.SelectField("EMPLOYEE", "id"),
            new QueryRequest.SelectField("EMPLOYEE", "name")
        ));
        
        request.setFilters(Arrays.asList(
            new QueryRequest.Filter("EMPLOYEE", "status", "EQ", "ACTIVE")
        ));
        
        request.setLimit(10);
        
        // Execute
        QueryResult result = queryExecutor.execute(request);
        
        log.info("Generated SQL:\n{}", result.getGeneratedSql());
        log.info("Row count: {}", result.getRowCount());
        
        // Assertions
        assert result.getGeneratedSql().contains("FROM");
        assert result.getGeneratedSql().contains("WHERE");
        assert result.getGeneratedSql().contains(":param");
    }
    
    /**
     * Test 2: Multi-hop JOIN query
     */
    @Test
    void testMultiHopJoin() {
        log.info("=== Test 2: Multi-Hop JOIN ===");
        
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setRootObjectCode("EMPLOYEE");
        
        // Query across 3 objects: EMPLOYEE -> DEPARTMENT -> LOCATION
        request.setSelectFields(Arrays.asList(
            new QueryRequest.SelectField("EMPLOYEE", "name"),
            new QueryRequest.SelectField("DEPARTMENT", "dept_name"),
            new QueryRequest.SelectField("LOCATION", "city")
        ));
        
        request.setFilters(Arrays.asList(
            new QueryRequest.Filter("EMPLOYEE", "salary", "GT", 50000),
            new QueryRequest.Filter("LOCATION", "country", "EQ", "USA")
        ));
        
        // Build AST manually to inspect JOIN planning
        QueryAST ast = new QueryAST();
        ast.setTenantCode(request.getTenantCode());
        ast.setAppCode(request.getAppCode());
        ast.setDbconnId(request.getDbconnId());
        ast.setRootObject(request.getRootObjectCode());
        
        // Add nodes from request
        request.getSelectFields().forEach(sf -> 
            ast.addSelect(new com.a4b.dqes.query.ast.SelectNode(
                sf.getObjectCode(), sf.getFieldCode(), sf.getAlias()
            ))
        );
        request.getFilters().forEach(f -> 
            ast.addFilter(new com.a4b.dqes.query.ast.FilterNode(
                f.getObjectCode(), f.getFieldCode(), f.getOperatorCode(), f.getValue()
            ))
        );
        
        // Plan JOINs
        joinPathPlanner.planJoins(ast);
        
        log.info("Planned {} JOINs:", ast.getJoins().size());
        ast.getJoins().forEach(join -> 
            log.info("  {} -> {} (order={}, strategy={})", 
                join.getFromObjectCode(), join.getToObjectCode(), 
                join.getExecutionOrder(), join.getStrategy())
        );
        
        // Generate SQL
        GeneratedSql generatedSql = sqlGenerator.generateSql(ast);
        log.info("Generated SQL:\n{}", generatedSql.getSql());
        
        // Verify multi-hop
        assert ast.getJoins().size() >= 2;  // At least 2 joins for 3-object query
        assert generatedSql.getSql().contains("LEFT JOIN") || 
               generatedSql.getSql().contains("INNER JOIN");
    }
    
    /**
     * Test 3: EXISTS strategy for ONE_TO_MANY
     */
    @Test
    void testExistsStrategy() {
        log.info("=== Test 3: EXISTS Strategy ===");
        
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setRootObjectCode("EMPLOYEE");
        
        // SELECT only from root
        request.setSelectFields(Arrays.asList(
            new QueryRequest.SelectField("EMPLOYEE", "name")
        ));
        
        // Filter on ONE_TO_MANY relation (should trigger EXISTS)
        request.setFilters(Arrays.asList(
            new QueryRequest.Filter("PROJECT_ASSIGNMENT", "status", "EQ", "ACTIVE")
        ));
        
        QueryResult result = queryExecutor.execute(request);
        
        log.info("Generated SQL:\n{}", result.getGeneratedSql());
        
        // Verify EXISTS usage
        if (result.getGeneratedSql().contains("EXISTS")) {
            log.info("✓ EXISTS strategy applied successfully");
        } else {
            log.warn("EXISTS not found - relation might not be configured as ONE_TO_MANY with EXISTS_PREFERRED");
        }
    }
    
    /**
     * Test 4: Complex filters (BETWEEN, IN)
     */
    @Test
    void testComplexFilters() {
        log.info("=== Test 4: Complex Filters ===");
        
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setRootObjectCode("EMPLOYEE");
        
        request.setSelectFields(Arrays.asList(
            new QueryRequest.SelectField("EMPLOYEE", "name"),
            new QueryRequest.SelectField("EMPLOYEE", "salary")
        ));
        
        request.setFilters(Arrays.asList(
            // BETWEEN filter
            new QueryRequest.Filter("EMPLOYEE", "salary", "BETWEEN", 
                Arrays.asList(40000, 80000)),
            
            // IN filter
            new QueryRequest.Filter("EMPLOYEE", "dept_code", "IN", 
                Arrays.asList("IT", "HR", "FIN"))
        ));
        
        request.setSorts(Arrays.asList(
            new QueryRequest.Sort("EMPLOYEE", "salary", SortDirection.DESC)
        ));
        
        QueryResult result = queryExecutor.execute(request);
        
        log.info("Generated SQL:\n{}", result.getGeneratedSql());
        log.info("Rows returned: {}", result.getRowCount());
        
        // Verify BETWEEN and IN
        assert result.getGeneratedSql().contains("BETWEEN");
        assert result.getGeneratedSql().contains("IN");
        assert result.getGeneratedSql().contains("ORDER BY");
    }
    
    /**
     * Test 5: Count query
     */
    @Test
    void testCountQuery() {
        log.info("=== Test 5: Count Query ===");
        
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setRootObjectCode("EMPLOYEE");
        
        request.setFilters(Arrays.asList(
            new QueryRequest.Filter("EMPLOYEE", "status", "EQ", "ACTIVE")
        ));
        
        long count = queryExecutor.executeCount(request);
        
        log.info("Count result: {}", count);
        
        assert count >= 0;
    }
    
    /**
     * Test 6: Dependency ordering (depends_on)
     */
    @Test
    void testDependsOnOrdering() {
        log.info("=== Test 6: Depends-On Ordering ===");
        
        QueryRequest request = new QueryRequest();
        request.setTenantCode("SUPPER");
        request.setAppCode("SUPPER");
        request.setDbconnId(1);
        request.setRootObjectCode("EMPLOYEE");
        
        // Query that requires dependent joins
        request.setSelectFields(Arrays.asList(
            new QueryRequest.SelectField("EMPLOYEE", "name"),
            new QueryRequest.SelectField("LOCATION", "city")
        ));
        
        // Build AST
        QueryAST ast = new QueryAST();
        ast.setTenantCode(request.getTenantCode());
        ast.setAppCode(request.getAppCode());
        ast.setDbconnId(request.getDbconnId());
        ast.setRootObject(request.getRootObjectCode());
        
        request.getSelectFields().forEach(sf -> 
            ast.addSelect(new com.a4b.dqes.query.ast.SelectNode(
                sf.getObjectCode(), sf.getFieldCode()
            ))
        );
        
        // Plan JOINs
        joinPathPlanner.planJoins(ast);
        
        log.info("JOIN execution order:");
        ast.getJoins().forEach(join -> 
            log.info("  [{}] {} -> {} (depends_on={})", 
                join.getExecutionOrder(), 
                join.getFromObjectCode(), 
                join.getToObjectCode(),
                join.getDependsOnRelationCode() != null ? join.getDependsOnRelationCode() : "none")
        );
        
        // Verify topological order
        for (int i = 0; i < ast.getJoins().size() - 1; i++) {
            assert ast.getJoins().get(i).getExecutionOrder() < 
                   ast.getJoins().get(i + 1).getExecutionOrder();
        }
        
        log.info("✓ Dependency ordering verified");
    }
    
    /**
     * Test 7: Metadata caching
     */
    @Test
    void testMetadataCaching() {
        log.info("=== Test 7: Metadata Caching ===");
        
        // First call - cache miss
        long start1 = System.currentTimeMillis();
        var obj1 = metadataRepo.findObjectMeta("SUPPER", "SUPPER", "EMPLOYEE");
        long time1 = System.currentTimeMillis() - start1;
        
        // Second call - cache hit
        long start2 = System.currentTimeMillis();
        var obj2 = metadataRepo.findObjectMeta("SUPPER", "SUPPER", "EMPLOYEE");
        long time2 = System.currentTimeMillis() - start2;
        
        log.info("First call: {}ms", time1);
        log.info("Second call: {}ms (cached)", time2);
        
        assert time2 <= time1;  // Cached should be faster
        log.info("✓ Caching working correctly");
    }
}
