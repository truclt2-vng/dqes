# Dynamic Query Engine - Architecture & Usage

## Overview

A production-grade dynamic query engine built on PostgreSQL metadata schema with:
- **Multi-hop graph joins** via BFS path planning
- **Named parameter binding** for SQL injection safety
- **EXISTS strategy** for ONE_TO_MANY filter optimization
- **Dependency-aware JOIN ordering** (topological sort)
- **Expression sandbox** via allowlist templates

## Architecture

```
QueryRequest (JSON)
    ↓
DynamicQueryExecutor
    ↓
QueryAST Builder
    ↓
JoinPathPlanner (BFS + depends_on)
    ↓
SqlGenerator (NamedParameterJdbcTemplate)
    ↓
Generated SQL + Parameters
    ↓
NamedParameterJdbcTemplate.query()
    ↓
QueryResult (JSON)
```

## Key Components

### 1. AST Models (`com.a4b.dqes.query.ast`)
- **QueryAST**: Root node containing SELECT/WHERE/ORDER BY/JOINs
- **SelectNode**: Field selection with optional alias
- **FilterNode**: WHERE predicate (field OP value)
- **SortNode**: ORDER BY with direction & NULLS ordering
- **JoinNode**: JOIN definition with strategy (JOIN/EXISTS)

### 2. Metadata Repository (`com.a4b.dqes.query.metadata`)
Loads metadata from `dqes` schema with Spring caching:
- **ObjectMeta**: Table/view definitions
- **RelationMeta**: JOIN relationships with join keys
- **FieldMeta**: Column/expression mappings
- **ObjectPathCache**: Pre-computed BFS shortest paths
- **ExprAllowlist**: Safe expression templates

### 3. JoinPathPlanner (`com.a4b.dqes.query.planner`)
Plans multi-hop JOIN graph:
1. Collect referenced objects from SELECT/WHERE/ORDER BY
2. Lookup shortest paths from `qrytb_object_path_cache`
3. Load relation metadata with join keys
4. Apply EXISTS strategy for ONE_TO_MANY filter-only
5. Topological sort based on `depends_on_code`

**EXISTS Strategy Rules:**
- `EXISTS_ONLY`: Always use EXISTS
- `EXISTS_PREFERRED`: Use EXISTS if object is filter-only
- `AUTO`: Use EXISTS for ONE_TO_MANY filter-only
- `JOIN_ONLY`: Always use standard JOIN

### 4. SqlGenerator (`com.a4b.dqes.query.generator`)
Generates safe SQL:
- Runtime alias allocation (`t0`, `t1`, `t2`, ...)
- Named parameter binding (`:param0`, `:param1`, ...)
- EXISTS subquery generation
- Expression template substitution
- Proper NULL handling (IS NOT DISTINCT FROM)

### 5. DynamicQueryExecutor (`com.a4b.dqes.query`)
Main service orchestrating the pipeline:
- `execute(QueryRequest)`: Full query execution
- `executeCount(QueryRequest)`: Count-only query

## Usage Examples

### Example 1: Simple Query
```java
QueryRequest request = new QueryRequest();
request.setTenantCode("SUPPER");
request.setAppCode("SUPPER");
request.setDbconnId(1);
request.setRootObjectCode("EMPLOYEE");

// SELECT id, name, salary
request.setSelectFields(Arrays.asList(
    new QueryRequest.SelectField("EMPLOYEE", "id"),
    new QueryRequest.SelectField("EMPLOYEE", "name"),
    new QueryRequest.SelectField("EMPLOYEE", "salary")
));

// WHERE status = 'ACTIVE'
request.setFilters(Arrays.asList(
    new QueryRequest.Filter("EMPLOYEE", "status", "EQ", "ACTIVE")
));

// ORDER BY name ASC
request.setSorts(Arrays.asList(
    new QueryRequest.Sort("EMPLOYEE", "name", SortDirection.ASC)
));

QueryResult result = queryExecutor.execute(request);
```

**Generated SQL:**
```sql
SELECT t0.id AS EMPLOYEE_id, t0.name AS EMPLOYEE_name, t0.salary AS EMPLOYEE_salary
FROM core.employee t0
WHERE t0.status = :param0
ORDER BY t0.name ASC NULLS LAST
```

### Example 2: Multi-Hop JOIN
```java
QueryRequest request = new QueryRequest();
request.setRootObjectCode("EMPLOYEE");

// SELECT from 3 objects → triggers multi-hop JOIN
request.setSelectFields(Arrays.asList(
    new QueryRequest.SelectField("EMPLOYEE", "name"),
    new QueryRequest.SelectField("DEPARTMENT", "dept_name"),
    new QueryRequest.SelectField("LOCATION", "city")
));

// Filters across objects
request.setFilters(Arrays.asList(
    new QueryRequest.Filter("EMPLOYEE", "salary", "GT", 50000),
    new QueryRequest.Filter("DEPARTMENT", "dept_code", "IN", Arrays.asList("IT", "HR")),
    new QueryRequest.Filter("LOCATION", "country", "EQ", "USA")
));
```

**Path Planning:**
1. Planner finds: `EMPLOYEE -> DEPARTMENT -> LOCATION`
2. Loads relations: `REL_EMP_DEPT`, `REL_DEPT_LOC`
3. Checks `depends_on_code` for ordering
4. Generates topologically sorted JOINs

**Generated SQL:**
```sql
SELECT t0.name AS EMPLOYEE_name, t1.dept_name AS DEPARTMENT_dept_name, t2.city AS LOCATION_city
FROM core.employee t0
LEFT JOIN core.department t1 ON t0.dept_id = t1.id
LEFT JOIN core.location t2 ON t1.location_id = t2.id
WHERE t0.salary > :param0
  AND t1.dept_code IN (:param1)
  AND t2.country = :param2
```

### Example 3: EXISTS Strategy (ONE_TO_MANY)
```java
QueryRequest request = new QueryRequest();
request.setRootObjectCode("EMPLOYEE");

// SELECT only from root
request.setSelectFields(Arrays.asList(
    new QueryRequest.SelectField("EMPLOYEE", "name")
));

// Filter on ONE_TO_MANY relation
request.setFilters(Arrays.asList(
    new QueryRequest.Filter("PROJECT_ASSIGNMENT", "status", "EQ", "ACTIVE")
));
```

**Path Planning:**
- Relation `EMPLOYEE -> PROJECT_ASSIGNMENT` is ONE_TO_MANY
- `PROJECT_ASSIGNMENT` is filter-only (not selected/sorted)
- Strategy: Use EXISTS subquery

**Generated SQL:**
```sql
SELECT t0.name AS EMPLOYEE_name
FROM core.employee t0
WHERE EXISTS (
  SELECT 1 FROM core.project_assignment sq_project_assignment
  WHERE sq_project_assignment.employee_id = t0.id
    AND sq_project_assignment.status = :param0
)
```

### Example 4: Complex Filters
```java
// BETWEEN filter
request.setFilters(Arrays.asList(
    new QueryRequest.Filter("EMPLOYEE", "hire_date", "BETWEEN", 
        Arrays.asList("2020-01-01", "2023-12-31")),
    new QueryRequest.Filter("EMPLOYEE", "salary", "BETWEEN", 
        Arrays.asList(40000, 80000))
));
```

**Generated SQL:**
```sql
WHERE t0.hire_date BETWEEN :param0 AND :param1
  AND t0.salary BETWEEN :param2 AND :param3
```

## Supported Operators

From `qrytb_operation_meta`:
- `EQ`, `NE`: Equals, Not equals
- `GT`, `GE`, `LT`, `LE`: Comparison
- `IN`, `NOT_IN`: List membership
- `BETWEEN`: Range (requires 2-element array)
- `LIKE`, `ILIKE`: Pattern matching
- `IS_NULL`, `IS_NOT_NULL`: NULL checks

## REST API

### Execute Query
```http
POST /api/dqes/query/execute
Content-Type: application/json

{
  "tenantCode": "SUPPER",
  "appCode": "SUPPER",
  "dbconnId": 1,
  "rootObjectCode": "CORE_COMTB_CODE_GROUP",
  "selectFields": [
    {"objectCode": "CORE_COMTB_CODE_GROUP", "fieldCode": "CODE"},{"objectCode": "CORE_COMTB_CODE_GROUP", "fieldCode": "NAME"}
  ],
  "filters": [
    {"objectCode": "CORE_COMTB_CODE_GROUP", "fieldCode": "RECORD_STATUS", "operatorCode": "EQ", "value": "O"}
  ],
  "limit": 100
}
```

**Response:**
```json
{
  "rows": [
    {"EMPLOYEE_name": "John Doe"},
    {"EMPLOYEE_name": "Jane Smith"}
  ],
  "rowCount": 2,
  "generatedSql": "SELECT t0.name AS EMPLOYEE_name FROM ...",
  "aliasMap": {"EMPLOYEE": "t0"}
}
```

### Get Count
```http
POST /api/dqes/query/count
Content-Type: application/json

{
  "tenantCode": "SUPPER",
  "appCode": "SUPPER",
  "dbconnId": 1,
  "rootObjectCode": "EMPLOYEE",
  "filters": [...]
}
```

**Response:**
```json
1234
```

## Metadata Setup

### 1. Run SQL Schema
```sql
-- Execute fulltable_normalized.sql
\i src/main/resources/sql/fulltable_normalized.sql
```

### 2. Define Objects
```sql
INSERT INTO dqes.qrytb_object_meta (object_code, object_name, db_table, dbconn_id, tenant_code, app_code)
VALUES ('EMPLOYEE', 'Employee', 'core.employee', 1, 'SUPPER', 'SUPPER');
```

### 3. Define Relations
```sql
-- EMPLOYEE -> DEPARTMENT (MANY_TO_ONE)
INSERT INTO dqes.qrytb_relation_info 
  (code, from_object_code, to_object_code, relation_type, join_type, filter_mode, dbconn_id, tenant_code, app_code)
VALUES 
  ('REL_EMP_DEPT', 'EMPLOYEE', 'DEPARTMENT', 'MANY_TO_ONE', 'LEFT', 'AUTO', 1, 'SUPPER', 'SUPPER');

-- Join keys
INSERT INTO dqes.qrytb_relation_join_key 
  (relation_id, seq, from_column_name, to_column_name, dbconn_id, tenant_code, app_code)
VALUES 
  (1, 1, 'dept_id', 'id', 1, 'SUPPER', 'SUPPER');
```

### 4. Define Fields
```sql
INSERT INTO dqes.qrytb_field_meta 
  (object_code, field_code, field_label, mapping_type, column_name, data_type, 
   allow_select, allow_filter, allow_sort, tenant_code, app_code)
VALUES 
  ('EMPLOYEE', 'name', 'Employee Name', 'COLUMN', 'name', 'STRING', true, true, true, 'SUPPER', 'SUPPER');
```

### 5. Refresh Path Cache
```sql
CALL dqes.refresh_qry_object_paths('SUPPER', 'SUPPER', 1, 6);
```

This runs BFS to pre-compute all shortest paths between objects.

## Security Features

1. **SQL Injection Prevention**: Named parameters for all values
2. **Expression Sandbox**: Only allowlisted templates from `qrytb_expr_allowlist`
3. **No Raw SQL**: Structured metadata (no `join_on` SQL strings)
4. **Type Safety**: Data types mapped to Java types for binding
5. **Role-Based Access**: `roles_allowed` field in `qrytb_field_meta`

## Performance Optimization

1. **Path Cache**: Pre-computed BFS paths avoid runtime graph traversal
2. **Spring Caching**: Metadata cached in memory (L1/L2)
3. **EXISTS Strategy**: Optimizes ONE_TO_MANY filters (no JOIN + GROUP BY)
4. **Dependency Sort**: Efficient JOIN execution order
5. **Pagination**: LIMIT/OFFSET support

## Extension Points

### Custom Expressions
Add to `qrytb_expr_allowlist`:
```sql
INSERT INTO dqes.qrytb_expr_allowlist 
  (expr_code, expr_type, sql_template, allow_in_select, allow_in_filter, 
   min_args, max_args, tenant_code, app_code)
VALUES 
  ('YEAR', 'TEMPLATE', 'EXTRACT(YEAR FROM {0})', true, true, 1, 1, 'SUPPER', 'SUPPER');
```

### Custom Data Types
Add to `qrytb_data_type`:
```sql
INSERT INTO dqes.qrytb_data_type (code, java_type, pg_cast, tenant_code, app_code)
VALUES ('GEOMETRY', 'org.locationtech.jts.geom.Geometry', 'geometry', 'SUPPER', 'SUPPER');
```

### Custom Operators
Add to `qrytb_operation_meta` + `qrytb_data_type_op`:
```sql
INSERT INTO dqes.qrytb_operation_meta (code, op_symbol, op_label, arity, value_shape, tenant_code, app_code)
VALUES ('CONTAINS', '@>', 'Contains', 1, 'SCALAR', 'SUPPER', 'SUPPER');
```

## Files Created

**AST Models:**
- `QueryAST.java` - Root AST node
- `SelectNode.java` - SELECT field
- `FilterNode.java` - WHERE predicate
- `SortNode.java` - ORDER BY item
- `JoinNode.java` - JOIN definition

**Metadata:**
- `ObjectMeta.java` - Table metadata
- `RelationMeta.java` - Relation metadata
- `FieldMeta.java` - Field metadata
- `ObjectPathCache.java` - Path cache
- `ExprAllowlist.java` - Expression templates
- `DqesMetadataRepository.java` - Repository with caching

**Query Engine:**
- `JoinPathPlanner.java` - BFS path planner
- `SqlGenerator.java` - SQL generator
- `DynamicQueryExecutor.java` - Main executor service
- `QueryRequest.java` - Request DTO
- `QueryResult.java` - Result DTO

**API:**
- `DynamicQueryResource.java` - REST controller
- `DynamicQueryExample.java` - Usage examples

## License

Part of A4B mcrxhrm project - JHipster 8.1.0 microservice
