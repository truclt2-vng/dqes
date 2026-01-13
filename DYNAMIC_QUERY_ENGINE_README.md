# Dynamic Query Engine

A sophisticated query engine for building dynamic SQL queries with multi-hop joins, advanced filtering, and Redis caching.

## Features

### Core Capabilities

✅ **NamedParameterJdbcTemplate Integration**
- Type-safe parameter binding
- Protection against SQL injection
- Named parameter support for readability

✅ **Multi-Hop Graph Joins**
- Automatic relationship traversal using Dijkstra's algorithm
- Weighted path optimization
- Support for complex object graphs
- Configurable join types (INNER, LEFT)

✅ **Advanced Operators**
- Standard: `EQ`, `NE`, `GT`, `LT`, `GTE`, `LTE`
- Collections: `IN`, `NOT_IN`
- Patterns: `LIKE`, `NOT_LIKE`
- Null handling: `IS_NULL`, `IS_NOT_NULL`
- Ranges: `BETWEEN`
- **Subqueries: `EXISTS`, `NOT_EXISTS`**

✅ **Redis Caching**
- Metadata caching (24-hour TTL)
- Query result caching (15-minute TTL)
- Path resolution caching (6-hour TTL)
- Automatic cache invalidation support

✅ **Alias Hint Support**
- User-friendly alias hints from metadata
- Runtime alias generation for disambiguation
- Consistent naming across queries

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                   DynamicQueryController                     │
│                    (REST Endpoint)                           │
└────────────────────┬────────────────────────────────────────┘
                     │
                     v
┌─────────────────────────────────────────────────────────────┐
│            DynamicQueryExecutionService                      │
│         (Orchestrates query execution)                       │
└────┬──────────────┬──────────────┬─────────────────────┬────┘
     │              │              │                     │
     v              v              v                     v
┌─────────┐  ┌─────────────┐ ┌────────────┐  ┌──────────────┐
│ Field   │  │  Relation   │ │   SQL      │  │NamedParameter│
│Resolver │  │   Graph     │ │  Query     │  │JdbcTemplate  │
│ Service │  │  Service    │ │  Builder   │  │              │
└─────────┘  └─────────────┘ └────────────┘  └──────────────┘
     │              │              │
     v              v              v
┌─────────────────────────────────────────────────────────────┐
│                    Redis Cache Layer                         │
│  - Metadata Cache    - Query Path Cache                     │
│  - Query Result Cache                                        │
└─────────────────────────────────────────────────────────────┘
     │
     v
┌─────────────────────────────────────────────────────────────┐
│                PostgreSQL Database                           │
│  - qrytb_object_meta   - qrytb_field_meta                   │
│  - qrytb_relation_info - qrytb_operation_meta               │
└─────────────────────────────────────────────────────────────┘
```

### Key Classes

#### DTOs
- **DynamicQueryRequest**: Query request with filters, sorts, pagination
- **FilterCriteria**: Filter specification with operator and values
- **DynamicQueryResult**: Query results with metadata and execution stats

#### Entities
- **ObjectMeta**: Object metadata with alias hints
- **FieldMeta**: Field metadata with column mappings
- **RelationInfo**: Relationship definitions for graph traversal
- **OperationMeta**: Operator definitions and validation

#### Services
- **DynamicQueryExecutionService**: Main orchestration service
- **FieldResolverService**: Resolves field paths to metadata
- **RelationGraphService**: Multi-hop path resolution using Dijkstra
- **SqlQueryBuilder**: Builds safe SQL with named parameters

## API Usage

### Endpoint

```
POST /api/v1/dynamic-query/execute
```

### Request Example

```json
{
  "tenantCode": "SUPPER",
  "appCode": "SUPPER",
  "dbconnId": 1,
  "rootObject": "employee",
  "selectFields": [
    "employee.employeeCode",
    "gender.name",
    "nationality.code"
  ],
  "filters": [
    {
      "field": "worker.code",
      "operatorCode": "EQ",
      "value": "EMP02"
    }
  ],
  "offset": 0,
  "limit": 100
}
```

### Response Example

```json
{
  "data": [
    {
      "employeeCode": "EMP02",
      "name": "Male",
      "code": "US"
    }
  ],
  "totalCount": 1,
  "offset": 0,
  "limit": 100,
  "executedSql": "SELECT e.employee_code AS emp_code, g.name AS gender_name, n.code AS nat_code FROM dqes.employee e LEFT JOIN dqes.gender g ON e.gender_id = g.id LEFT JOIN dqes.nationality n ON e.nationality_id = n.id WHERE e.code = :param_0 LIMIT :limit OFFSET :offset",
  "parameters": {
    "param_0": "EMP02",
    "limit": 100,
    "offset": 0
  },
  "executionTimeMs": 45
}
```

## Configuration

### Redis Configuration

Configure Redis connection in `application.yml`:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: your-password
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms
```

### Database Configuration

Ensure the following tables exist (see `fulltable_normalized_v2.sql`):
- `dqes.qrytb_object_meta` - Object definitions
- `dqes.qrytb_field_meta` - Field definitions
- `dqes.qrytb_relation_info` - Relationship definitions
- `dqes.qrytb_relation_join_key` - Join key definitions
- `dqes.qrytb_operation_meta` - Operator definitions

## Advanced Features

### Multi-Hop Joins

The engine automatically resolves paths through the relation graph:

```json
{
  "selectFields": [
    "employee.name",
    "department.name",
    "department.location.city"
  ]
}
```

This automatically creates:
```sql
FROM employee e
LEFT JOIN department d ON e.department_id = d.id
LEFT JOIN location l ON d.location_id = l.id
```

### EXISTS / NOT EXISTS

Support for correlated subqueries:

```json
{
  "filters": [
    {
      "field": "employee",
      "operatorCode": "EXISTS",
      "subQuery": {
        "rootObject": "project_assignment",
        "filters": [
          {
            "field": "project.status",
            "operatorCode": "EQ",
            "value": "ACTIVE"
          }
        ]
      }
    }
  ]
}
```

### Complex Filters

Nested AND/OR conditions:

```json
{
  "filters": [
    {
      "logicalOperator": "AND",
      "subFilters": [
        {
          "field": "employee.status",
          "operatorCode": "EQ",
          "value": "ACTIVE"
        },
        {
          "logicalOperator": "OR",
          "subFilters": [
            {
              "field": "employee.department",
              "operatorCode": "EQ",
              "value": "IT"
            },
            {
              "field": "employee.department",
              "operatorCode": "EQ",
              "value": "HR"
            }
          ]
        }
      ]
    }
  ]
}
```

### Caching Strategy

1. **Metadata Cache** (24h TTL)
   - Object metadata
   - Field metadata
   - Relation definitions
   - Operation definitions

2. **Path Cache** (6h TTL)
   - Relation graph paths
   - Join resolution results

3. **Query Results** (15m TTL)
   - Actual query results
   - Cached by request hash

## Performance Optimization

### Graph Path Resolution
- Dijkstra's algorithm for optimal path finding
- Weighted paths for join preference
- Cached path results

### SQL Generation
- Named parameters for prepared statement reuse
- Minimal JOIN generation (only required paths)
- Optimal alias allocation

### Caching
- Multi-layer caching strategy
- Different TTLs based on data volatility
- Request-level cache keys

## Dependencies

```xml
<!-- Added to pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

## Testing

### Unit Tests
Test individual components:
- Field resolution
- Path finding
- SQL building
- Operator validation

### Integration Tests
Test end-to-end scenarios:
- Simple queries
- Multi-hop joins
- Complex filters
- Cache behavior

## Future Enhancements

- [ ] Expression-based fields (CONCAT, COALESCE, etc.)
- [ ] Aggregation support (GROUP BY, HAVING)
- [ ] Window functions
- [ ] Query optimization hints
- [ ] Query plan visualization
- [ ] Performance metrics dashboard
- [ ] Dynamic schema discovery

## Security Considerations

✅ SQL Injection Protection: Named parameters only
✅ Type Safety: Operator validation against metadata
✅ Tenant Isolation: Tenant/App code enforcement
✅ Query Complexity Limits: Configurable max joins/depth

## License

Copyright (c) 2026 A4B Solutions
