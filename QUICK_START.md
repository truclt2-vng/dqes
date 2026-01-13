# Dynamic Query Engine - Quick Start Guide

## Prerequisites

1. PostgreSQL database running
2. Redis server running
3. Java 25 (or configured version)
4. Maven 3.2.5+

## Setup Steps

### 1. Database Setup

```bash
# Connect to PostgreSQL
psql -U postgres

# Run the schema creation script
\i src/main/resources/sql/fulltable_normalized_v2.sql

# Load sample data
\i src/main/resources/sql/sample_query_engine_data.sql
```

### 2. Redis Setup

```bash
# Install Redis (if not already installed)
# macOS
brew install redis
brew services start redis

# Linux
sudo apt-get install redis-server
sudo systemctl start redis

# Windows
# Download from https://redis.io/download
```

### 3. Application Configuration

Update `src/main/resources/config/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/dqes
    username: postgres
    password: your_password
    
  redis:
    host: localhost
    port: 6379
    password: # leave empty if no password
    database: 0
```

### 4. Build and Run

```bash
# Build the project
./mvnw clean install

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Testing the API

### 1. Health Check

```bash
curl http://localhost:8080/api/v1/dynamic-query/health
```

### 2. Simple Query - Employee List

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "SUPPER",
    "appCode": "SUPPER",
    "dbconnId": 1,
    "rootObject": "employee",
    "selectFields": [
      "employee.employeeCode",
      "employee.employeeName"
    ],
    "offset": 0,
    "limit": 100
  }'
```

### 3. Query with Single Join

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "SUPPER",
    "appCode": "SUPPER",
    "dbconnId": 1,
    "rootObject": "employee",
    "selectFields": [
      "employee.employeeCode",
      "employee.employeeName",
      "gender.name"
    ],
    "offset": 0,
    "limit": 100
  }'
```

### 4. Query with Multi-Hop Joins

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "SUPPER",
    "appCode": "SUPPER",
    "dbconnId": 1,
    "rootObject": "employee",
    "selectFields": [
      "employee.employeeCode",
      "employee.employeeName",
      "gender.name",
      "nationality.code",
      "department.deptName"
    ],
    "offset": 0,
    "limit": 100
  }'
```

### 5. Query with Filters

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "SUPPER",
    "appCode": "SUPPER",
    "dbconnId": 1,
    "rootObject": "employee",
    "selectFields": [
      "employee.employeeCode",
      "employee.employeeName",
      "gender.name"
    ],
    "filters": [
      {
        "field": "employee.status",
        "operatorCode": "EQ",
        "value": "ACTIVE"
      }
    ],
    "offset": 0,
    "limit": 100
  }'
```

### 6. Query with Complex Filters

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "SUPPER",
    "appCode": "SUPPER",
    "dbconnId": 1,
    "rootObject": "employee",
    "selectFields": [
      "employee.employeeCode",
      "employee.employeeName",
      "department.deptName"
    ],
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
                "field": "department.deptCode",
                "operatorCode": "EQ",
                "value": "IT"
              },
              {
                "field": "department.deptCode",
                "operatorCode": "EQ",
                "value": "HR"
              }
            ]
          }
        ]
      }
    ],
    "offset": 0,
    "limit": 100
  }'
```

### 7. Query with IN Operator

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "SUPPER",
    "appCode": "SUPPER",
    "dbconnId": 1,
    "rootObject": "employee",
    "selectFields": [
      "employee.employeeCode",
      "employee.employeeName"
    ],
    "filters": [
      {
        "field": "employee.employeeCode",
        "operatorCode": "IN",
        "values": ["EMP01", "EMP02"]
      }
    ],
    "offset": 0,
    "limit": 100
  }'
```

### 8. Query Using Worker Reference (from example)

```bash
curl -X POST http://localhost:8080/api/v1/dynamic-query/execute \
  -H "Content-Type: application/json" \
  -d '{
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
  }'
```

## Expected Response Format

```json
{
  "data": [
    {
      "emp_code": "EMP01",
      "emp_name": "John Doe",
      "gen_name": "Male",
      "nat_code": "US",
      "dept_name": "Information Technology"
    }
  ],
  "totalCount": 1,
  "offset": 0,
  "limit": 100,
  "executedSql": "SELECT emp0.employee_code AS emp_code, emp0.employee_name AS emp_name, gen1.name AS gen_name, nat2.code AS nat_code, dept3.dept_name AS dept_name FROM dqes.employee emp0 LEFT JOIN dqes.gender gen1 ON emp0.gender_id = gen1.id LEFT JOIN dqes.nationality nat2 ON emp0.nationality_id = nat2.id LEFT JOIN dqes.department dept3 ON emp0.department_id = dept3.id WHERE emp0.status = :param_0 LIMIT :limit OFFSET :offset",
  "parameters": {
    "param_0": "ACTIVE",
    "limit": 100,
    "offset": 0
  },
  "executionTimeMs": 25
}
```

## Monitoring Cache

### View Redis Cache Keys

```bash
# Connect to Redis
redis-cli

# List all cache keys
KEYS dqes:*

# View specific cache
GET "dqes:objectMetaByCode::SUPPER_SUPPER_employee"

# Clear all cache
FLUSHDB
```

## Troubleshooting

### Issue: Connection refused to PostgreSQL

```bash
# Check if PostgreSQL is running
pg_isready

# Check connection settings in application-local.yml
```

### Issue: Redis connection failed

```bash
# Check if Redis is running
redis-cli ping
# Should return: PONG

# Check Redis connection settings
```

### Issue: No path found between objects

- Verify relation metadata exists in `qrytb_relation_info`
- Check that relations are marked as `is_navigable = true`
- Ensure `current_flg = true` for active relations

### Issue: Field not found

- Verify field metadata exists in `qrytb_field_meta`
- Check `object_code` and `field_code` match exactly
- Ensure `current_flg = true`

## Next Steps

1. Add more object and field metadata for your domain
2. Define relationships between objects
3. Configure operators and data types as needed
4. Implement expression-based fields
5. Add authentication and authorization
6. Set up monitoring and logging

## Support

For issues or questions, refer to:
- [DYNAMIC_QUERY_ENGINE_README.md](DYNAMIC_QUERY_ENGINE_README.md) - Detailed documentation
- [fulltable_normalized_v2.sql](src/main/resources/sql/fulltable_normalized_v2.sql) - Database schema
