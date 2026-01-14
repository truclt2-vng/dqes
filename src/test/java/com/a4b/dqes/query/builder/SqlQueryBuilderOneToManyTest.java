package com.a4b.dqes.query.builder;

import com.a4b.dqes.domain.FieldMeta;
import com.a4b.dqes.domain.ObjectMeta;
import com.a4b.dqes.domain.RelationInfo;
import com.a4b.dqes.domain.RelationJoinKey;
import com.a4b.dqes.query.model.QueryContext;
import com.a4b.dqes.query.model.RelationPath;
import com.a4b.dqes.query.model.ResolvedField;
import com.a4b.dqes.repository.jpa.OperationMetaRepository;
import com.a4b.dqes.repository.jpa.RelationInfoRepository;
import com.a4b.dqes.repository.jpa.RelationJoinKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test for ONE_TO_MANY relation support with JSON_AGG aggregation
 */
@ExtendWith(MockitoExtension.class)
class SqlQueryBuilderOneToManyTest {

    @Mock
    private RelationInfoRepository relationInfoRepository;

    @Mock
    private RelationJoinKeyRepository relationJoinKeyRepository;

    @Mock
    private OperationMetaRepository operationMetaRepository;

    private SqlQueryBuilder sqlQueryBuilder;

    @BeforeEach
    void setUp() {
        sqlQueryBuilder = new SqlQueryBuilder(
            relationInfoRepository,
            relationJoinKeyRepository,
            operationMetaRepository
        );
    }

    @Test
    void testOneToManyRelationUsesJsonAgg() {
        // Setup context
        QueryContext context = createTestContext();

        // Setup metadata
        setupOrderMetadata();
        setupOrderItemMetadata();
        setupOneToManyRelation();

        // Create select fields: ORDER and ORDER_ITEM
        List<ResolvedField> selectFields = Arrays.asList(
            // Root order fields
            createResolvedField("ORDER", "id", "id", "order_id", "t0", null),
            createResolvedField("ORDER", "orderNumber", "order_number", "order_number", "t0", null),
            
            // ONE_TO_MANY order item fields
            createResolvedField("ORDER_ITEM", "id", "id", "item_id", "t1", createOrderToItemsPath()),
            createResolvedField("ORDER_ITEM", "productName", "product_name", "product_name", "t1", createOrderToItemsPath()),
            createResolvedField("ORDER_ITEM", "quantity", "quantity", "quantity", "t1", createOrderToItemsPath())
        );

        // Build query
        SqlQueryBuilder.SqlQuery query = sqlQueryBuilder.buildQuery(
            context,
            selectFields,
            null,  // no filters
            null,  // no offset
            null,  // no limit
            false  // not count only
        );

        String sql = query.getSql();

        // Assertions
        assertThat(sql)
            .as("SQL should contain COALESCE for empty array handling")
            .contains("COALESCE");

        assertThat(sql)
            .as("SQL should contain JSON_AGG for ONE_TO_MANY aggregation")
            .contains("JSON_AGG");

        assertThat(sql)
            .as("SQL should contain jsonb_build_object for child records")
            .contains("jsonb_build_object");

        assertThat(sql)
            .as("SQL should contain FILTER clause to exclude NULL joins")
            .contains("FILTER");

        assertThat(sql)
            .as("SQL should contain WHERE clause in FILTER")
            .containsPattern("FILTER\\s*\\(\\s*WHERE");

        assertThat(sql)
            .as("SQL should contain GROUP BY for root fields")
            .contains("GROUP BY");

        assertThat(sql)
            .as("SQL should group by root table fields")
            .containsPattern("GROUP BY\\s+t0\\.id,\\s+t0\\.order_number");

        // Should NOT contain multiple rows per parent
        assertThat(sql)
            .as("SQL should use LEFT JOIN (not CROSS JOIN)")
            .contains("LEFT JOIN");
    }

    @Test
    void testManyToOneRelationDoesNotUseJsonAgg() {
        // Setup context for MANY_TO_ONE (ORDER belongs to CUSTOMER)
        QueryContext context = createTestContext();

        setupOrderMetadata();
        setupCustomerMetadata();
        setupManyToOneRelation();

        List<ResolvedField> selectFields = Arrays.asList(
            createResolvedField("ORDER", "id", "id", "order_id", "t0", null),
            createResolvedField("CUSTOMER", "name", "name", "customer_name", "t1", createOrderToCustomerPath())
        );

        SqlQueryBuilder.SqlQuery query = sqlQueryBuilder.buildQuery(
            context,
            selectFields,
            null,
            null,
            null,
            false
        );

        String sql = query.getSql();

        // MANY_TO_ONE should NOT use JSON_AGG
        assertThat(sql)
            .as("MANY_TO_ONE should not use JSON_AGG")
            .doesNotContain("JSON_AGG");

        assertThat(sql)
            .as("MANY_TO_ONE should use jsonb_build_object")
            .contains("jsonb_build_object");
    }

    @Test
    void testMixedRelations() {
        // ORDER has MANY_TO_ONE to CUSTOMER and ONE_TO_MANY to ORDER_ITEM
        QueryContext context = createTestContext();

        setupOrderMetadata();
        setupCustomerMetadata();
        setupOrderItemMetadata();
        setupManyToOneRelation();
        setupOneToManyRelation();

        List<ResolvedField> selectFields = Arrays.asList(
            // Root order
            createResolvedField("ORDER", "id", "id", "order_id", "t0", null),
            
            // MANY_TO_ONE customer
            createResolvedField("CUSTOMER", "name", "name", "customer_name", "t1", createOrderToCustomerPath()),
            
            // ONE_TO_MANY items
            createResolvedField("ORDER_ITEM", "productName", "product_name", "product_name", "t2", createOrderToItemsPath())
        );

        SqlQueryBuilder.SqlQuery query = sqlQueryBuilder.buildQuery(
            context,
            selectFields,
            null,
            null,
            null,
            false
        );

        String sql = query.getSql();

        // Should have both jsonb_build_object (for CUSTOMER) and JSON_AGG (for ITEMS)
        assertThat(sql)
            .contains("jsonb_build_object")
            .contains("JSON_AGG")
            .contains("GROUP BY");

        // GROUP BY should include order fields and customer fields, but not item fields
        assertThat(sql)
            .containsPattern("GROUP BY.*t0\\.id")  // order id
            .containsPattern("GROUP BY.*t1\\.name");  // customer name (MANY_TO_ONE included)
    }

    // Helper methods

    private QueryContext createTestContext() {
        Map<String, ObjectMeta> objectMap = new HashMap<>();
        
        QueryContext context = QueryContext.builder()
            .tenantCode("TEST")
            .appCode("ERP")
            .dbconnId(1)
            .rootObject("ORDER")
            .rootTable("orders")
            .allObjectMetaMap(objectMap)
            .build();

        context.getOrGenerateAlias("ORDER", "t0");
        context.getObjectTables().put("ORDER", "orders");
        
        return context;
    }

    private void setupOrderMetadata() {
        ObjectMeta orderMeta = ObjectMeta.builder()
            .objectCode("ORDER")
            .dbTable("orders")
            .fieldMetas(Arrays.asList(
                createFieldMeta("ORDER", "id", "id"),
                createFieldMeta("ORDER", "orderNumber", "order_number"),
                createFieldMeta("ORDER", "totalAmount", "total_amount")
            ))
            .build();
        // Would be added to context in real scenario
    }

    private void setupOrderItemMetadata() {
        ObjectMeta itemMeta = ObjectMeta.builder()
            .objectCode("ORDER_ITEM")
            .dbTable("order_items")
            .fieldMetas(Arrays.asList(
                createFieldMeta("ORDER_ITEM", "id", "id"),
                createFieldMeta("ORDER_ITEM", "productName", "product_name"),
                createFieldMeta("ORDER_ITEM", "quantity", "quantity")
            ))
            .build();
    }

    private void setupCustomerMetadata() {
        ObjectMeta customerMeta = ObjectMeta.builder()
            .objectCode("CUSTOMER")
            .dbTable("customers")
            .fieldMetas(Arrays.asList(
                createFieldMeta("CUSTOMER", "id", "id"),
                createFieldMeta("CUSTOMER", "name", "name")
            ))
            .build();
    }

    private FieldMeta createFieldMeta(String objectCode, String fieldCode, String columnName) {
        return FieldMeta.builder()
            .objectCode(objectCode)
            .fieldCode(fieldCode)
            .columnName(columnName)
            .mappingType("COLUMN")
            .dataType("VARCHAR")
            .build();
    }

    private void setupOneToManyRelation() {
        RelationInfo relation = RelationInfo.builder()
            .code("ORDER_TO_ITEMS")
            .fromObjectCode("ORDER")
            .toObjectCode("ORDER_ITEM")
            .relationType("ONE_TO_MANY")  // Key: This makes it use JSON_AGG
            .joinType("LEFT")
            .build();

        RelationJoinKey joinKey = RelationJoinKey.builder()
            .fromColumnName("id")
            .toColumnName("order_id")
            .operator("=")
            .build();

        when(relationInfoRepository.findByTenantCodeAndAppCodeAndCode(any(), any(), any()))
            .thenReturn(Optional.of(relation));

        when(relationJoinKeyRepository.findByRelationIdOrderBySeq(any()))
            .thenReturn(Collections.singletonList(joinKey));
    }

    private void setupManyToOneRelation() {
        RelationInfo relation = RelationInfo.builder()
            .code("ORDER_TO_CUSTOMER")
            .fromObjectCode("ORDER")
            .toObjectCode("CUSTOMER")
            .relationType("MANY_TO_ONE")  // Not ONE_TO_MANY, so no JSON_AGG
            .joinType("LEFT")
            .build();

        when(relationInfoRepository.findByTenantCodeAndAppCodeAndCode(any(), any(), any()))
            .thenReturn(Optional.of(relation));
    }

    private ResolvedField createResolvedField(String objectCode, String fieldCode, String columnName,
                                             String aliasHint, String runtimeAlias, RelationPath relationPath) {
        return ResolvedField.builder()
            .objectCode(objectCode)
            .objectAlias(objectCode.toLowerCase())
            .fieldCode(fieldCode)
            .columnName(columnName)
            .aliasHint(aliasHint)
            .runtimeAlias(runtimeAlias)
            .mappingType("COLUMN")
            .relationPath(relationPath)
            .build();
    }

    private RelationPath createOrderToItemsPath() {
        RelationPath.PathStep step = RelationPath.PathStep.builder()
            .fromObject("ORDER")
            .toObject("ORDER_ITEM")
            .toAlias("t1")
            .relationCode("ORDER_TO_ITEMS")
            .joinType("LEFT")
            .build();

        return RelationPath.builder()
            .fromObject("ORDER")
            .toObject("ORDER_ITEM")
            .steps(Collections.singletonList(step))
            .build();
    }

    private RelationPath createOrderToCustomerPath() {
        RelationPath.PathStep step = RelationPath.PathStep.builder()
            .fromObject("ORDER")
            .toObject("CUSTOMER")
            .toAlias("t1")
            .relationCode("ORDER_TO_CUSTOMER")
            .joinType("LEFT")
            .build();

        return RelationPath.builder()
            .fromObject("ORDER")
            .toObject("CUSTOMER")
            .steps(Collections.singletonList(step))
            .build();
    }
}
