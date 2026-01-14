# ONE_TO_MANY Relation Support

## Overview

The Dynamic Query Engine now supports **ONE_TO_MANY relations** that return as **list items** without increasing row count. This is achieved using PostgreSQL's `JSON_AGG` aggregation function combined with `GROUP BY` clauses.

## Problem Statement

Previously, when querying entities with ONE_TO_MANY relationships using `LEFT JOIN`, the result set would multiply rows:

```sql
-- OLD BEHAVIOR: Row multiplication
SELECT parent.id, parent.name, child.id, child.name
FROM parent 
LEFT JOIN child ON parent.id = child.parent_id

-- Result (if parent #1 has 3 children):
-- Row 1: parent_id=1, child_id=1
-- Row 2: parent_id=1, child_id=2  
-- Row 3: parent_id=1, child_id=3  (3 rows for 1 parent!)
```

## Solution

The system now automatically detects ONE_TO_MANY relations and uses aggregation:

```sql
-- NEW BEHAVIOR: Aggregation without row multiplication
SELECT 
  parent.id, 
  parent.name,
  COALESCE(
    JSON_AGG(
      jsonb_build_object('id', child.id, 'name', child.name)
    ) FILTER (WHERE child.id IS NOT NULL), 
    '[]'::jsonb
  ) AS children
FROM parent 
LEFT JOIN child ON parent.id = child.parent_id
GROUP BY parent.id, parent.name

-- Result (same parent with 3 children):
-- Row 1: parent_id=1, children=[{id:1, name:"..."}, {id:2, name:"..."}, {id:3, name:"..."}]
```

## Implementation Details

### 1. Relation Detection

In `SqlQueryBuilder.detectOneToManyObjects()`:
- Scans all `ResolvedField` objects
- Checks each relation's `RelationType` from metadata
- Returns a set of object codes that are ONE_TO_MANY children

### 2. SELECT Clause Generation

In `SqlQueryBuilder.buildSelectClause()`:
- **Root objects**: Fields selected directly (no change)
- **MANY_TO_ONE/ONE_TO_ONE**: Uses `jsonb_build_object()` (no change)
- **ONE_TO_MANY**: Wraps in `JSON_AGG()` with:
  - `COALESCE()` to return empty array `[]` when no children
  - `FILTER (WHERE child.id IS NOT NULL)` to exclude NULL joins

```java
// For ONE_TO_MANY relations:
COALESCE(
  JSON_AGG(
    jsonb_build_object('field1', value1, 'field2', value2, ...)
  ) FILTER (WHERE child_table.id IS NOT NULL),
  '[]'::jsonb
) AS relationName
```

### 3. GROUP BY Clause

In `SqlQueryBuilder.buildGroupByClause()`:
- Groups by all **root object fields**
- Groups by all **MANY_TO_ONE/ONE_TO_ONE** related fields
- Excludes **ONE_TO_MANY** fields (they're aggregated)

```sql
GROUP BY 
  parent.id, 
  parent.name, 
  many_to_one_table.id,
  many_to_one_table.field
```

## Usage Example

### API Request

```json
{
  "tenantCode": "ACME",
  "appCode": "ERP",
  "dbconnId": 1,
  "rootObject": "ORDER",
  "selectFields": [
    "ORDER.id",
    "ORDER.orderNumber",
    "ORDER.totalAmount",
    "ORDER_ITEM.id",
    "ORDER_ITEM.productName",
    "ORDER_ITEM.quantity",
    "ORDER_ITEM.price"
  ]
}
```

### Generated SQL

```sql
SELECT 
  t0.id AS order_id,
  t0.order_number AS order_number,
  t0.total_amount AS total_amount,
  COALESCE(
    JSON_AGG(
      jsonb_build_object(
        'id', t1.id,
        'productName', t1.product_name,
        'quantity', t1.quantity,
        'price', t1.price
      )
    ) FILTER (WHERE t1.id IS NOT NULL),
    '[]'::jsonb
  ) AS orderItems
FROM orders t0
LEFT JOIN order_items t1 ON t0.id = t1.order_id
GROUP BY t0.id, t0.order_number, t0.total_amount
```

### Response

```json
{
  "data": [
    {
      "orderId": 1,
      "orderNumber": "ORD-001",
      "totalAmount": 1500.00,
      "orderItems": [
        {"id": 1, "productName": "Laptop", "quantity": 1, "price": 1000.00},
        {"id": 2, "productName": "Mouse", "quantity": 2, "price": 250.00}
      ]
    },
    {
      "orderId": 2,
      "orderNumber": "ORD-002",
      "totalAmount": 500.00,
      "orderItems": [
        {"id": 3, "productName": "Keyboard", "quantity": 1, "price": 500.00}
      ]
    }
  ],
  "totalCount": 2
}
```

## Key Benefits

1. **No Row Multiplication**: Each parent appears once regardless of child count
2. **Clean JSON Structure**: Children returned as nested arrays
3. **Handles Empty Collections**: Returns `[]` when no children exist
4. **Maintains NULL Safety**: Uses `FILTER` to exclude NULL joins
5. **Performance**: Single query with aggregation (no N+1 problems)

## Relation Type Handling

| Relation Type | Join Strategy | Aggregation | GROUP BY Required |
|--------------|---------------|-------------|-------------------|
| **MANY_TO_ONE** | LEFT JOIN | No | Included in GROUP BY |
| **ONE_TO_ONE** | LEFT JOIN | No | Included in GROUP BY |
| **ONE_TO_MANY** | LEFT JOIN | **JSON_AGG** | **Excluded** |
| **MANY_TO_MANY** | LEFT JOIN | JSON_AGG (future) | Excluded |

## Configuration

### Relation Metadata

Ensure your `qrytb_relation_info` table has correct `relation_type` values:

```sql
INSERT INTO dqes.qrytb_relation_info (
  tenant_code, app_code, code,
  from_object_code, to_object_code,
  relation_type,  -- 'ONE_TO_MANY' for parent->children
  join_type
) VALUES (
  'ACME', 'ERP', 'ORDER_TO_ITEMS',
  'ORDER', 'ORDER_ITEM',
  'ONE_TO_MANY',
  'LEFT'
);
```

### Primary Key Detection

The system auto-detects primary keys by:
1. Looking for fields with "id" or "ID" in name
2. Using first field as fallback
3. Assuming "id" column as last resort

You can improve detection by ensuring ID fields follow naming conventions:
- `id`, `order_id`, `customer_id`, etc.

## Performance Considerations

### Advantages
- ✅ **Single query** eliminates N+1 problems
- ✅ **Reduced network overhead** (fewer rows)
- ✅ **Easier pagination** (parent-level)

### Limitations
- ⚠️ **Large child collections**: May create large JSON objects
- ⚠️ **Complex GROUP BY**: Can be expensive with many parent fields
- ⚠️ **Sorting children**: Requires ordering within JSON_AGG

### Optimization Tips

```sql
-- Add indexes on foreign keys
CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- For sorted children, use ORDER BY in JSON_AGG
JSON_AGG(
  jsonb_build_object(...)
  ORDER BY child.sequence_number
)
```

## Troubleshooting

### Issue: "column must appear in GROUP BY clause"

**Cause**: Selected a non-aggregated field without including it in GROUP BY

**Solution**: Ensure all root and MANY_TO_ONE fields are in the select list

### Issue: Getting `[null]` instead of `[]`

**Cause**: Missing `FILTER (WHERE ... IS NOT NULL)` or `COALESCE`

**Solution**: System automatically adds these - check relation metadata

### Issue: Duplicate rows still appearing

**Cause**: Relation not detected as ONE_TO_MANY

**Solution**: Verify `relation_type` in `qrytb_relation_info` table is exactly `'ONE_TO_MANY'`

## Future Enhancements

- [ ] Support for MANY_TO_MANY with double aggregation
- [ ] Configurable aggregation functions (ARRAY_AGG, STRING_AGG)
- [ ] Nested ONE_TO_MANY (children with grandchildren)
- [ ] Custom ordering within aggregated collections
- [ ] Pagination within child collections

## Related Files

- **SqlQueryBuilder.java** - Main query builder with aggregation logic
- **DynamicQueryExecutionService.java** - Query execution and result normalization
- **RelationInfo.java** - Relation metadata entity
- **QueryContext.java** - Query context with relation paths

## References

- PostgreSQL JSON Functions: https://www.postgresql.org/docs/current/functions-json.html
- JSON_AGG: https://www.postgresql.org/docs/current/functions-aggregate.html
