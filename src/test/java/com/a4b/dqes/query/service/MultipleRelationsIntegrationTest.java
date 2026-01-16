package com.a4b.dqes.query.service;

import com.a4b.dqes.query.dto.DynamicQueryRequest;
import com.a4b.dqes.query.dto.DynamicQueryResult;
import com.a4b.dqes.query.dto.FilterCriteria;
import com.a4b.dqes.query.dto.SortCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for multiple relations to same target object
 * Scenario: emptbEmployee has multiple lookups to comtbCodeList
 * - employeeClass
 * - workerCategory
 * - gender
 * - nationality
 */
@SpringBootTest
@ActiveProfiles("test")
class MultipleRelationsIntegrationTest {

    @Autowired
    private DynamicQueryExecutionService queryExecutionService;

    private static final String TENANT = "SUPPER";
    private static final String APP = "SUPPER";
    private static final Integer DBCONN_ID = 1;

    /**
     * Test 1: Select fields from multiple relations to same object
     */
    @Test
    void testSelectFromMultipleRelations() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .selectFields(Arrays.asList(
                "emptbEmployee.employeeCode",
                "employeeClass.code",      // ⭐ joinAlias 1
                "workerCategory.code"     // ⭐ joinAlias 2
            ))
            .limit(10)
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        assertThat(result).isNotNull();
        assertThat(result.getData()).isNotNull();
        
        // Verify SQL contains all 4 JOINs with correct aliases
        String sql = result.getExecutedSql();
        assertThat(sql).containsIgnoringCase("LEFT JOIN")
            .containsIgnoringCase("employeeClass")
            .containsIgnoringCase("workerCategory")
        ;

        // Verify each JOIN is to comtb_code_list
        assertThat(sql).containsPattern("(?i)comtb_code_list\\s+employeeClass");
        assertThat(sql).containsPattern("(?i)comtb_code_list\\s+workerCategory");

        // Verify data structure
        if (!result.getData().isEmpty()) {
            Map<String, Object> firstRow = result.getData().get(0);
            assertThat(firstRow).containsKeys(
                "employeeCode",
                "fullName",
                "employeeClass_code",
                "workerCategory_code"
            );
        }
    }

    /**
     * Test 2: Filter on multiple relations
     */
    @Test
    void testFilterOnMultipleRelations() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .selectFields(Arrays.asList(
                "emptbEmployee.employeeCode",
                "emptbEmployee.fullName"
            ))
            .filters(Arrays.asList(
                FilterCriteria.builder()
                    .field("employeeClass.code")
                    .operator("EQ")
                    .value("CLASS_A")
                    .build(),
                FilterCriteria.builder()
                    .logicalOperator("AND")
                    .field("workerCategory.code")
                    .operator("IN")
                    .value(Arrays.asList("CAT_WORKER", "CAT_CONTRACTOR"))
                    .build(),
                FilterCriteria.builder()
                    .logicalOperator("AND")
                    .field("gender.code")
                    .operator("EQ")
                    .value("MALE")
                    .build()
            ))
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        assertThat(result).isNotNull();
        
        // Verify SQL contains filters on correct aliases
        String sql = result.getExecutedSql();
        assertThat(sql).containsIgnoringCase("WHERE")
            .containsIgnoringCase("employeeClass.code_value")
            .containsIgnoringCase("workerCategory.code_value")
            .containsIgnoringCase("gender.code_value");

        // Verify all 3 relations are joined
        assertThat(sql).containsIgnoringCase("comtb_code_list employeeClass");
        assertThat(sql).containsIgnoringCase("comtb_code_list workerCategory");
        assertThat(sql).containsIgnoringCase("comtb_code_list gender");
    }

    /**
     * Test 3: Mix select and filter on different relations
     */
    @Test
    void testMixSelectAndFilterOnDifferentRelations() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .selectFields(Arrays.asList(
                "emptbEmployee.employee_code",
                "employeeClass.code_name",      // Select from relation 1
                "gender.code_name"              // Select from relation 2
            ))
            .filters(Arrays.asList(
                FilterCriteria.builder()
                    .field("workerCategory.code_value")  // Filter on relation 3
                    .operator("EQ")
                    .value("CAT_WORKER")
                    .build(),
                FilterCriteria.builder()
                    .logicalOperator("AND")
                    .field("nationality.code_value")     // Filter on relation 4
                    .operator("EQ")
                    .value("VN")
                    .build()
            ))
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        assertThat(result).isNotNull();
        
        // Should have all 4 JOINs (2 for select, 2 for filter)
        String sql = result.getExecutedSql();
        assertThat(sql).containsIgnoringCase("employeeClass");
        assertThat(sql).containsIgnoringCase("gender");
        assertThat(sql).containsIgnoringCase("workerCategory");
        assertThat(sql).containsIgnoringCase("nationality");
    }

    /**
     * Test 4: Sort by fields from multiple relations
     */
    @Test
    void testSortByMultipleRelations() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .selectFields(Arrays.asList(
                "emptbEmployee.employee_code",
                "emptbEmployee.full_name",
                "employeeClass.code_name",
                "workerCategory.code_name"
            ))
            .sorts(Arrays.asList(
                SortCriteria.builder()
                    .field("employeeClass.code_name")
                    .direction("ASC")
                    .build(),
                SortCriteria.builder()
                    .field("workerCategory.code_name")
                    .direction("ASC")
                    .build()
            ))
            .limit(10)
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        assertThat(result).isNotNull();
        
        String sql = result.getExecutedSql();
        assertThat(sql).containsIgnoringCase("ORDER BY")
            .containsIgnoringCase("employeeClass.code_name")
            .containsIgnoringCase("workerCategory.code_name");
    }

    /**
     * Test 5: Count query with multiple relations
     */
    @Test
    void testCountWithMultipleRelations() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .filters(Arrays.asList(
                FilterCriteria.builder()
                    .field("employeeClass.code_value")
                    .operator("EQ")
                    .value("CLASS_A")
                    .build(),
                FilterCriteria.builder()
                    .logicalOperator("AND")
                    .field("gender.code_value")
                    .operator("EQ")
                    .value("MALE")
                    .build()
            ))
            .countOnly(true)
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        assertThat(result).isNotNull();
        assertThat(result.getTotalCount()).isNotNull();
        
        String sql = result.getExecutedSql();
        assertThat(sql).containsIgnoringCase("COUNT(*)");
        assertThat(sql).containsIgnoringCase("employeeClass");
        assertThat(sql).containsIgnoringCase("gender");
    }

    /**
     * Test 6: Verify unique aliases even with same target object
     */
    @Test
    void testUniqueAliasesForSameTargetObject() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .selectFields(Arrays.asList(
                "emptbEmployee.employee_code",
                "employeeClass.code_name",
                "workerCategory.code_name"
            ))
            .filters(Arrays.asList(
                FilterCriteria.builder()
                    .field("employeeClass.code_value")
                    .operator("EQ")
                    .value("CLASS_A")
                    .build()
            ))
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        String sql = result.getExecutedSql().toLowerCase();
        
        // Count occurrences of each alias in SQL
        int employeeClassCount = countOccurrences(sql, "employeeclass");
        int workerCategoryCount = countOccurrences(sql, "workercategory");
        
        // Each alias should appear multiple times (in JOIN, SELECT, WHERE)
        assertThat(employeeClassCount).isGreaterThan(1);
        assertThat(workerCategoryCount).isGreaterThan(1);
        
        // Should have exactly 2 JOINs to comtb_code_list
        int joinCount = countOccurrences(sql, "join");
        assertThat(joinCount).isEqualTo(2);
    }

    /**
     * Test 7: Complex nested filters with multiple relations
     */
    @Test
    void testNestedFiltersWithMultipleRelations() {
        DynamicQueryRequest request = DynamicQueryRequest.builder()
            .tenantCode(TENANT)
            .appCode(APP)
            .dbconnId(DBCONN_ID)
            .rootObject("emptbEmployee")
            .selectFields(List.of("emptbEmployee.employee_code"))
            .filters(Arrays.asList(
                FilterCriteria.builder()
                    .logicalOperator("AND")
                    .subFilters(Arrays.asList(
                        FilterCriteria.builder()
                            .field("employeeClass.code_value")
                            .operator("IN")
                            .value(Arrays.asList("CLASS_A", "CLASS_B"))
                            .build(),
                        FilterCriteria.builder()
                            .logicalOperator("OR")
                            .field("workerCategory.code_value")
                            .operator("EQ")
                            .value("CAT_WORKER")
                            .build()
                    ))
                    .build(),
                FilterCriteria.builder()
                    .logicalOperator("AND")
                    .field("gender.code_value")
                    .operator("EQ")
                    .value("MALE")
                    .build()
            ))
            .build();

        DynamicQueryResult result = queryExecutionService.executeQuery(request);

        assertThat(result).isNotNull();
        
        String sql = result.getExecutedSql();
        // Should contain all 3 relations
        assertThat(sql).containsIgnoringCase("employeeClass");
        assertThat(sql).containsIgnoringCase("workerCategory");
        assertThat(sql).containsIgnoringCase("gender");
        
        // Should have proper parentheses for nested conditions
        assertThat(sql).containsPattern("(?i)\\(.*employeeClass.*OR.*workerCategory.*\\)");
    }

    // Helper method
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
