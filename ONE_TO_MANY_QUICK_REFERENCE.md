# ONE_TO_MANY Implementation - Quick Reference

## What Changed?

The Dynamic Query Engine now supports **ONE_TO_MANY relations** that return as **arrays** instead of multiplying rows.

## Modified Files

### 1. SqlQueryBuilder.java
**Location**: `dqes/src/main/java/com/a4b/dqes/query/builder/SqlQueryBuilder.java`

**Key Changes**:
- Added `detectOneToManyObjects()` - Detects relations with type `ONE_TO_MANY`
- Modified `buildSelectClause()` - Wraps ONE_TO_MANY fields in `JSON_AGG()`
- Added `getPrimaryKeyColumn()` - Gets PK for FILTER clause
- Added `buildGroupByClause()` - Generates GROUP BY for aggregation

### 2. Documentation
- `ONE_TO_MANY_SUPPORT.md` - Complete technical documentation
- `ONE_TO_MANY_EXAMPLES.md` - SQL examples and comparisons

### 3. Test
- `SqlQueryBuilderOneToManyTest.java` - Unit tests for validation

---

## How It Works

### Detection Phase
```java
// 1. Scans selectFields for relation paths
// 2. Queries RelationInfo for each relation
// 3. Identifies relations with relationType = "ONE_TO_MANY"
Set<String> oneToManyObjects = detectOneToManyObjects(selectFields, context);
```

### SELECT Phase
```java
// Root object fields: SELECT field1, field2, ...
// MANY_TO_ONE: jsonb_build_object('field1', value1, ...)
// ONE_TO_MANY: COALESCE(JSON_AGG(jsonb_build_object(...)) FILTER (WHERE pk IS NOT NULL), '[]')
```

### GROUP BY Phase
```java
// Groups by:
// - All root object fields
// - All MANY_TO_ONE/ONE_TO_ONE fields
// Excludes:
// - ONE_TO_MANY fields (they're aggregated)
```

---

## Usage

### 1. Setup Relation Metadata

```sql
-- Define the ONE_TO_MANY relation
INSERT INTO dqes.qrytb_relation_info (
  tenant_code, app_code, code,
  from_object_code, to_object_code,
  relation_type,  -- THIS IS KEY!
  join_type
) VALUES (
  'ACME', 'ERP', 'ORDER_TO_ITEMS',
  'ORDER', 'ORDER_ITEM',
  'ONE_TO_MANY',  -- ← Must be exactly this value
  'LEFT'
);

-- Define join keys
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id, seq,
  from_column_name, to_column_name,
  operator
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info WHERE code = 'ORDER_TO_ITEMS'),
  1,
  'id', 'order_id',
  '='
);
```

### 2. API Request

```json
{
  "tenantCode": "ACME",
  "appCode": "ERP",
  "dbconnId": 1,
  "rootObject": "ORDER",
  "selectFields": [
    "ORDER.id",
    "ORDER.orderNumber",
    "ORDER_ITEM.id",           // ← Child fields
    "ORDER_ITEM.productName",  // ← Will be aggregated
    "ORDER_ITEM.quantity"      // ← into an array
  ]
}
```

### 3. Generated SQL

```sql
SELECT 
  t0.id AS order_id,
  t0.order_number AS order_number,
  COALESCE(
    JSON_AGG(
      jsonb_build_object(
        'id', t1.id,
        'productName', t1.product_name,
        'quantity', t1.quantity
      )
    ) FILTER (WHERE t1.id IS NOT NULL),
    '[]'::jsonb
  ) AS orderItems
FROM orders t0
LEFT JOIN order_items t1 ON t0.id = t1.order_id
GROUP BY t0.id, t0.order_number;
```

### 4. Response

```json
{
  "data": [
    {
      "orderId": 1,
      "orderNumber": "ORD-001",
      "orderItems": [
        {"id": 1, "productName": "Laptop", "quantity": 1},
        {"id": 2, "productName": "Mouse", "quantity": 2}
      ]
    }
  ]
}
```

---

## Relation Type Behavior

| Type | Strategy | Result |
|------|----------|--------|
| `MANY_TO_ONE` | LEFT JOIN + jsonb_build_object | Single object |
| `ONE_TO_ONE` | LEFT JOIN + jsonb_build_object | Single object |
| **`ONE_TO_MANY`** | **LEFT JOIN + JSON_AGG** | **Array of objects** |

---

## Important Notes

### ✅ Correct Usage

```java
// Relation metadata
relationType = "ONE_TO_MANY"  // Exact string match

// Primary key detection
// System looks for fields containing "id" in name:
// - "id" ✅
// - "order_id" ✅  
// - "customer_id" ✅
// - "pk" ❌ (won't be detected)
```

### ⚠️ Common Issues

**Issue**: Still getting multiple rows
- **Cause**: `relation_type` not set to `"ONE_TO_MANY"`
- **Fix**: Check database: `SELECT relation_type FROM dqes.qrytb_relation_info`

**Issue**: Getting `[null]` instead of `[]`
- **Cause**: Missing FILTER or COALESCE (shouldn't happen with new code)
- **Fix**: Verify using latest SqlQueryBuilder

**Issue**: "column must appear in GROUP BY"
- **Cause**: Selected a non-aggregated field without including it
- **Fix**: Ensure all root fields are in select list

---

## Testing

### Manual Test Query

```sql
-- Verify ONE_TO_MANY detection
SELECT 
  code,
  from_object_code,
  to_object_code,
  relation_type
FROM dqes.qrytb_relation_info
WHERE relation_type = 'ONE_TO_MANY';
```

### Run Unit Tests

```bash
cd dqes
mvn test -Dtest=SqlQueryBuilderOneToManyTest
```

---

## Performance Tips

### 1. Index Foreign Keys
```sql
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

### 2. Limit Child Records
```sql
-- If you need top 10 items per order, use subquery:
JSON_AGG(
  SELECT jsonb_build_object(...)
  FROM (
    SELECT * FROM order_items oi2
    WHERE oi2.order_id = o.id
    ORDER BY oi2.price DESC
    LIMIT 10
  ) sub
)
```

### 3. Monitor GROUP BY Cost
```sql
-- Use EXPLAIN ANALYZE to check performance
EXPLAIN ANALYZE
SELECT ... FROM ... GROUP BY ...;
```

---

## Checklist for New ONE_TO_MANY Relations

- [ ] Create/update RelationInfo with `relation_type = 'ONE_TO_MANY'`
- [ ] Create RelationJoinKey entries
- [ ] Ensure child table has PK with "id" in name
- [ ] Test with empty collections (no children)
- [ ] Test with multiple children
- [ ] Verify JSON structure in response
- [ ] Check query performance with EXPLAIN
- [ ] Update API documentation
- [ ] Notify frontend team of structure change

---

## Support

For issues or questions:
1. Check `ONE_TO_MANY_SUPPORT.md` for detailed documentation
2. Review `ONE_TO_MANY_EXAMPLES.md` for SQL comparisons
3. Run unit tests: `SqlQueryBuilderOneToManyTest`
4. Check relation metadata in database

---

## Future Enhancements

- [ ] MANY_TO_MANY support with double aggregation
- [ ] Nested ONE_TO_MANY (grandchildren)
- [ ] Custom sort within aggregated arrays
- [ ] Pagination within child collections
- [ ] Alternative aggregation functions (ARRAY_AGG, STRING_AGG)
