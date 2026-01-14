# ONE_TO_MANY SQL Examples - Before & After

## Example 1: Simple One-to-Many (Order → Order Items)

### Before (Row Multiplication)

**Query:**
```sql
SELECT 
  o.id AS order_id,
  o.order_number,
  o.total_amount,
  oi.id AS item_id,
  oi.product_name,
  oi.quantity,
  oi.price
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE o.customer_id = 123;
```

**Result (3 items → 3 rows):**
```
order_id | order_number | total_amount | item_id | product_name | quantity | price
---------|--------------|--------------|---------|--------------|----------|-------
   1     | ORD-001      | 1500.00      |   1     | Laptop       | 1        | 1000
   1     | ORD-001      | 1500.00      |   2     | Mouse        | 2        | 250
   1     | ORD-001      | 1500.00      |   3     | Cable        | 5        | 50
```
**Problem**: Order data duplicated 3 times!

---

### After (JSON Aggregation)

**Query:**
```sql
SELECT 
  o.id AS order_id,
  o.order_number,
  o.total_amount,
  COALESCE(
    JSON_AGG(
      jsonb_build_object(
        'itemId', oi.id,
        'productName', oi.product_name,
        'quantity', oi.quantity,
        'price', oi.price
      )
    ) FILTER (WHERE oi.id IS NOT NULL),
    '[]'::jsonb
  ) AS items
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE o.customer_id = 123
GROUP BY o.id, o.order_number, o.total_amount;
```

**Result (3 items → 1 row):**
```
order_id | order_number | total_amount | items
---------|--------------|--------------|-------
   1     | ORD-001      | 1500.00      | [{"itemId":1,"productName":"Laptop",...}, ...]
```
**Benefit**: Single row with nested array!

---

## Example 2: Mixed Relations (Customer ← Order → Order Items)

### Schema
```
CUSTOMER (1) ←──[MANY_TO_ONE]──  ORDER (1) ──[ONE_TO_MANY]──→ ORDER_ITEM (N)
```

### Before (Row Multiplication)

**Query:**
```sql
SELECT 
  c.id AS customer_id,
  c.name AS customer_name,
  o.id AS order_id,
  o.order_number,
  oi.id AS item_id,
  oi.product_name
FROM customers c
INNER JOIN orders o ON c.id = o.customer_id
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE c.id = 100;
```

**Result (2 orders, 5 total items):**
```
customer_id | customer_name | order_id | order_number | item_id | product_name
------------|---------------|----------|--------------|---------|---------------
   100      | John Doe      |   1      | ORD-001      |   1     | Laptop
   100      | John Doe      |   1      | ORD-001      |   2     | Mouse
   100      | John Doe      |   1      | ORD-001      |   3     | Cable
   100      | John Doe      |   2      | ORD-002      |   4     | Keyboard
   100      | John Doe      |   2      | ORD-002      |   5     | Monitor
```
**Problem**: Customer data repeated 5 times!

---

### After (Partial Aggregation at Order Level)

**Option A: Aggregate only items**
```sql
SELECT 
  c.id AS customer_id,
  c.name AS customer_name,
  o.id AS order_id,
  o.order_number,
  COALESCE(
    JSON_AGG(
      jsonb_build_object('itemId', oi.id, 'productName', oi.product_name)
    ) FILTER (WHERE oi.id IS NOT NULL),
    '[]'::jsonb
  ) AS items
FROM customers c
INNER JOIN orders o ON c.id = o.customer_id
LEFT JOIN order_items oi ON o.id = oi.order_id
WHERE c.id = 100
GROUP BY c.id, c.name, o.id, o.order_number;
```

**Result (2 rows):**
```
customer_id | customer_name | order_id | order_number | items
------------|---------------|----------|--------------|--------
   100      | John Doe      |   1      | ORD-001      | [{...}, {...}, {...}]
   100      | John Doe      |   2      | ORD-002      | [{...}, {...}]
```

**Option B: Nested aggregation (orders as array too)**
```sql
SELECT 
  c.id AS customer_id,
  c.name AS customer_name,
  JSON_AGG(
    jsonb_build_object(
      'orderId', o.id,
      'orderNumber', o.order_number,
      'items', (
        SELECT COALESCE(JSON_AGG(jsonb_build_object('itemId', oi.id, 'productName', oi.product_name)), '[]'::jsonb)
        FROM order_items oi
        WHERE oi.order_id = o.id
      )
    )
  ) AS orders
FROM customers c
INNER JOIN orders o ON c.id = o.customer_id
WHERE c.id = 100
GROUP BY c.id, c.name;
```

**Result (1 row):**
```json
{
  "customerId": 100,
  "customerName": "John Doe",
  "orders": [
    {
      "orderId": 1,
      "orderNumber": "ORD-001",
      "items": [
        {"itemId": 1, "productName": "Laptop"},
        {"itemId": 2, "productName": "Mouse"},
        {"itemId": 3, "productName": "Cable"}
      ]
    },
    {
      "orderId": 2,
      "orderNumber": "ORD-002",
      "items": [
        {"itemId": 4, "productName": "Keyboard"},
        {"itemId": 5, "productName": "Monitor"}
      ]
    }
  ]
}
```

---

## Example 3: Empty Collections

### Before (NULL values)

**Query:**
```sql
SELECT 
  o.id, 
  o.order_number,
  oi.id AS item_id
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id;
```

**Result (order with no items):**
```
id | order_number | item_id
---|--------------|--------
 3 | ORD-003      | NULL
```

---

### After (Empty Array)

**Query:**
```sql
SELECT 
  o.id, 
  o.order_number,
  COALESCE(
    JSON_AGG(oi.id) FILTER (WHERE oi.id IS NOT NULL),
    '[]'::jsonb
  ) AS item_ids
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.order_number;
```

**Result:**
```json
{
  "id": 3,
  "orderNumber": "ORD-003",
  "itemIds": []  // Empty array, not null!
}
```

---

## Example 4: Performance Comparison

### Dataset
- 1,000 customers
- 10,000 orders (avg 10 per customer)
- 50,000 order items (avg 5 per order)

### Before (Row Multiplication)
```sql
SELECT c.*, o.*, oi.*
FROM customers c
JOIN orders o ON c.id = o.customer_id
JOIN order_items oi ON o.id = oi.order_id;
```
- **Rows returned**: 50,000
- **Data transferred**: ~50 MB
- **Application memory**: High (dedupe needed)
- **Network roundtrips**: 1

### After (Aggregation)
```sql
SELECT 
  c.*,
  JSON_AGG(...) AS orders
FROM customers c
JOIN orders o ON c.id = o.customer_id
GROUP BY c.id;
```
- **Rows returned**: 1,000
- **Data transferred**: ~15 MB
- **Application memory**: Low (no dedupe)
- **Network roundtrips**: 1

**Performance Gain**: 50x fewer rows, 3x less data, much faster!

---

## Example 5: Filtering with Aggregation

### Scenario: Find customers with orders containing laptops

**Before (Row Multiplication + Distinct):**
```sql
SELECT DISTINCT c.id, c.name
FROM customers c
JOIN orders o ON c.id = o.customer_id
JOIN order_items oi ON o.id = oi.order_id
WHERE oi.product_name LIKE '%Laptop%';
```

**After (Aggregation with WHERE):**
```sql
SELECT 
  c.id,
  c.name,
  JSON_AGG(
    jsonb_build_object('orderId', o.id, 'orderNumber', o.order_number)
  ) AS orders_with_laptops
FROM customers c
JOIN orders o ON c.id = o.customer_id
JOIN order_items oi ON o.id = oi.order_id
WHERE oi.product_name LIKE '%Laptop%'
GROUP BY c.id, c.name;
```

---

## Example 6: Sorting Aggregated Data

### Sort items within order by price (descending)

```sql
SELECT 
  o.id,
  o.order_number,
  JSON_AGG(
    jsonb_build_object(
      'productName', oi.product_name,
      'price', oi.price
    )
    ORDER BY oi.price DESC  -- Sort within aggregation!
  ) AS items_by_price
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.order_number;
```

**Result:**
```json
{
  "id": 1,
  "orderNumber": "ORD-001",
  "itemsByPrice": [
    {"productName": "Laptop", "price": 1000},
    {"productName": "Mouse", "price": 250},
    {"productName": "Cable", "price": 50}
  ]
}
```

---

## Key Takeaways

| Aspect | Before (JOIN) | After (JSON_AGG) |
|--------|---------------|-------------------|
| **Rows** | N × M | N |
| **Duplicates** | Yes (parent data) | No |
| **Structure** | Flat | Nested |
| **Null Handling** | NULL values | Empty arrays `[]` |
| **Performance** | High memory/network | Low memory/network |
| **Complexity** | Simple SQL | Moderate SQL |
| **Frontend Work** | Grouping needed | Ready to use |

---

## Migration Checklist

- [ ] Identify all ONE_TO_MANY relations in metadata
- [ ] Update `relation_type` to `'ONE_TO_MANY'` in `qrytb_relation_info`
- [ ] Verify primary key detection (field names contain "id")
- [ ] Test queries with empty collections
- [ ] Monitor query performance (GROUP BY can be expensive)
- [ ] Update API documentation with new response structure
- [ ] Train frontend developers on nested JSON structure
