# Dynamic Data Operation Examples

## Service Usage (In Code)

### 1. Single Insert
```java
@Autowired
private DynamicDataOperationService dataOps;

// Insert a new user
Map<String, Object> userData = Map.of(
    "username", "john_doe",
    "email", "john@example.com",
    "age", 30,
    "created_at", LocalDateTime.now()
);

Long userId = dataOps.insert("MY_DB_CONN", "users", userData);
System.out.println("Inserted user with ID: " + userId);
```

### 2. Batch Insert
```java
List<Map<String, Object>> userList = List.of(
    Map.of("username", "alice", "email", "alice@example.com", "age", 25),
    Map.of("username", "bob", "email", "bob@example.com", "age", 35),
    Map.of("username", "charlie", "email", "charlie@example.com", "age", 28)
);

int count = dataOps.batchInsert("MY_DB_CONN", "users", userList);
System.out.println("Inserted " + count + " users");
```

### 3. Upsert (Insert or Update on Conflict)
```java
Map<String, Object> userData = Map.of(
    "id", 1,
    "username", "john_doe",
    "email", "john.new@example.com",
    "age", 31
);

int affected = dataOps.upsert(
    "MY_DB_CONN", 
    "users", 
    userData, 
    List.of("id") // Conflict on 'id' column
);
```

### 4. Update
```java
Map<String, Object> updates = Map.of(
    "email", "newemail@example.com",
    "age", 32
);

int updated = dataOps.update(
    "MY_DB_CONN",
    "users",
    updates,
    "id = :id",
    Map.of("id", 1)
);
```

### 5. Delete
```java
int deleted = dataOps.delete(
    "MY_DB_CONN",
    "users",
    "age < :minAge",
    Map.of("minAge", 18)
);
```

### 6. Query
```java
List<Map<String, Object>> users = dataOps.query(
    "MY_DB_CONN",
    "SELECT * FROM users WHERE age > :minAge",
    Map.of("minAge", 25)
);

users.forEach(user -> {
    System.out.println("User: " + user.get("username"));
});
```

### 7. Count
```java
long count = dataOps.count(
    "MY_DB_CONN",
    "users",
    "age >= :minAge",
    Map.of("minAge", 18)
);
System.out.println("Total users: " + count);
```

---

## REST API Usage

### 1. Insert Single Row
```bash
POST /api/dynamic-data/MY_DB_CONN/users
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "age": 30
}

# Response
{
  "generatedId": 123,
  "rowsAffected": 1
}
```

### 2. Batch Insert
```bash
POST /api/dynamic-data/MY_DB_CONN/users/batch
Content-Type: application/json

[
  {
    "username": "alice",
    "email": "alice@example.com",
    "age": 25
  },
  {
    "username": "bob",
    "email": "bob@example.com",
    "age": 35
  }
]

# Response
{
  "generatedId": null,
  "rowsAffected": 2
}
```

### 3. Upsert
```bash
POST /api/dynamic-data/MY_DB_CONN/users/upsert
Content-Type: application/json

{
  "data": {
    "id": 1,
    "username": "john_doe",
    "email": "john.new@example.com",
    "age": 31
  },
  "conflictColumns": ["id"]
}

# Response
{
  "generatedId": null,
  "rowsAffected": 1
}
```

### 4. Update
```bash
PUT /api/dynamic-data/MY_DB_CONN/users
Content-Type: application/json

{
  "data": {
    "email": "newemail@example.com",
    "age": 32
  },
  "whereClause": "id = :id",
  "whereParams": {
    "id": 1
  }
}

# Response
{
  "rowsAffected": 1
}
```

### 5. Delete
```bash
DELETE /api/dynamic-data/MY_DB_CONN/users
Content-Type: application/json

{
  "whereClause": "age < :minAge",
  "whereParams": {
    "minAge": 18
  }
}

# Response
{
  "rowsAffected": 5
}
```

### 6. Query
```bash
GET /api/dynamic-data/MY_DB_CONN/query?sql=SELECT%20*%20FROM%20users%20WHERE%20age%20%3E%20:minAge&minAge=25

# Response
[
  {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "age": 25
  },
  {
    "id": 2,
    "username": "bob",
    "email": "bob@example.com",
    "age": 35
  }
]
```

### 7. Count
```bash
GET /api/dynamic-data/MY_DB_CONN/users/count?whereClause=age%20%3E%3D%20:minAge&minAge=18

# Response
{
  "count": 42
}
```

---

## Advanced Examples

### Multi-Table Insert with Transaction
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final DynamicDataOperationService dataOps;
    
    @Transactional
    public void createOrder(String dbConnCode, OrderRequest order) {
        // Insert order
        Map<String, Object> orderData = Map.of(
            "customer_id", order.getCustomerId(),
            "total_amount", order.getTotalAmount(),
            "status", "PENDING",
            "created_at", LocalDateTime.now()
        );
        Long orderId = dataOps.insert(dbConnCode, "orders", orderData);
        
        // Insert order items
        List<Map<String, Object>> items = order.getItems().stream()
            .map(item -> Map.of(
                "order_id", orderId,
                "product_id", item.getProductId(),
                "quantity", item.getQuantity(),
                "price", item.getPrice()
            ))
            .toList();
        
        dataOps.batchInsert(dbConnCode, "order_items", items);
    }
}
```

### Dynamic Schema Operations
```java
// Insert into schema-qualified table
dataOps.insert("MY_DB_CONN", "analytics.user_events", Map.of(
    "user_id", 123,
    "event_type", "login",
    "timestamp", Instant.now()
));

// Query with JOIN
List<Map<String, Object>> results = dataOps.query(
    "MY_DB_CONN",
    """
    SELECT u.username, o.order_date, o.total_amount
    FROM users u
    JOIN orders o ON u.id = o.customer_id
    WHERE o.order_date > :startDate
    """,
    Map.of("startDate", LocalDate.now().minusDays(7))
);
```

### Conditional Updates
```java
// Update with multiple conditions
int updated = dataOps.update(
    "MY_DB_CONN",
    "products",
    Map.of(
        "price", 99.99,
        "discount_percent", 10,
        "updated_at", LocalDateTime.now()
    ),
    "category = :category AND stock > :minStock",
    Map.of("category", "ELECTRONICS", "minStock", 10)
);
```

---

## Security Considerations

### 1. SQL Injection Prevention
✅ **Safe** - Using named parameters:
```java
dataOps.query(dbConnCode, 
    "SELECT * FROM users WHERE username = :username",
    Map.of("username", userInput)
);
```

❌ **Unsafe** - String concatenation:
```java
// DON'T DO THIS!
String sql = "SELECT * FROM users WHERE username = '" + userInput + "'";
```

### 2. Access Control
Implement connection-level security:
```java
@Service
public class SecureDataOperationService {
    @Autowired
    private DynamicDataOperationService dataOps;
    
    @PreAuthorize("hasRole('ADMIN') or hasPermission(#dbConnCode, 'READ')")
    public List<Map<String, Object>> secureQuery(String dbConnCode, String sql, Map<String, Object> params) {
        return dataOps.query(dbConnCode, sql, params);
    }
}
```

### 3. Validate Input
```java
public void safeInsert(String dbConnCode, String tableName, Map<String, Object> data) {
    // Whitelist table names
    if (!ALLOWED_TABLES.contains(tableName)) {
        throw new SecurityException("Access to table " + tableName + " not allowed");
    }
    
    // Validate data
    validateData(data);
    
    dataOps.insert(dbConnCode, tableName, data);
}
```

---

## Performance Tips

1. **Use Batch Insert** for multiple rows (much faster than individual inserts)
2. **Upsert** is more efficient than separate SELECT + INSERT/UPDATE
3. **Index** columns used in WHERE clauses
4. **Limit** result sets in queries
5. **Connection pooling** is handled automatically by HikariCP

---

## Error Handling

```java
try {
    dataOps.insert("MY_DB_CONN", "users", userData);
} catch (DataAccessException e) {
    // Handle database errors
    log.error("Failed to insert user", e);
    
    if (e.getCause() instanceof SQLIntegrityConstraintViolationException) {
        // Handle duplicate key
    }
}
```
