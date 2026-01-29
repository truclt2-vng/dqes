# Error Handling Best Practices

## Overview

The Dynamic Data Operation Service now includes comprehensive error handling for common database constraint violations:

1. **Duplicate Key** - Unique constraint violations
2. **Foreign Key Violations** - Referenced records that can't be deleted
3. **Not Null Violations** - Required fields missing

## Exception Hierarchy

```
DataOperationException (base)
├── DuplicateKeyException
├── ForeignKeyViolationException
└── NotNullViolationException
```

## Error Response Format

All errors return a consistent JSON structure:

```json
{
  "status": 409,
  "error": "DUPLICATE_KEY",
  "message": "Duplicate entry: code=DY-TEST-001",
  "details": {
    "tableName": "core.comtb_code_group",
    "constraintOrColumn": "comtb_code_group_uk",
    "additionalInfo": "code=DY-TEST-001"
  },
  "timestamp": "2026-01-28T16:30:00"
}
```

## HTTP Status Codes

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| DuplicateKeyException | 409 CONFLICT | DUPLICATE_KEY |
| ForeignKeyViolationException | 409 CONFLICT | FOREIGN_KEY_VIOLATION |
| NotNullViolationException | 400 BAD_REQUEST | NOT_NULL_VIOLATION |
| DataOperationException | 500 INTERNAL_SERVER_ERROR | DATA_OPERATION_ERROR |
| IllegalArgumentException | 400 BAD_REQUEST | INVALID_ARGUMENT |

## Examples

### 1. Duplicate Key Violation

**Request:**
```bash
POST /api/dynamic-data/MY_DB/core.comtb_code_group
{
  "code": "DY-TEST-001",
  "name": "Test"
}
```

**Error Response (409):**
```json
{
  "status": 409,
  "error": "DUPLICATE_KEY",
  "message": "Duplicate entry: tenant_code, code=A4B, DY-TEST-001",
  "details": {
    "tableName": "core.comtb_code_group",
    "constraintOrColumn": "comtb_code_group_uk",
    "additionalInfo": "tenant_code, code=A4B, DY-TEST-001"
  },
  "timestamp": "2026-01-28T16:30:00"
}
```

**Client Handling:**
```java
try {
    dataOps.insert("MY_DB", "core.comtb_code_group", data);
} catch (DuplicateKeyException e) {
    // Handle duplicate - maybe update instead?
    log.warn("Record already exists: {}", e.getDuplicateKey());
    dataOps.upsert("MY_DB", "core.comtb_code_group", data, List.of("code"));
}
```

**TypeScript/JavaScript:**
```typescript
try {
  await api.post('/api/dynamic-data/MY_DB/core.comtb_code_group', data);
} catch (error) {
  if (error.response?.data?.error === 'DUPLICATE_KEY') {
    // Show user-friendly message
    alert(`Record with ${error.response.data.details.additionalInfo} already exists`);
  }
}
```

### 2. Foreign Key Violation

**Request:**
```bash
DELETE /api/dynamic-data/MY_DB/departments
{
  "whereClause": "id = :id",
  "whereParams": { "id": 1 }
}
```

**Error Response (409):**
```json
{
  "status": 409,
  "error": "FOREIGN_KEY_VIOLATION",
  "message": "Cannot delete: Record is still referenced by employees",
  "details": {
    "tableName": "departments",
    "constraintOrColumn": "fk_employee_department",
    "additionalInfo": "Referenced by: employees"
  },
  "timestamp": "2026-01-28T16:30:00"
}
```

**Client Handling:**
```java
try {
    dataOps.delete("MY_DB", "departments", "id = :id", Map.of("id", 1));
} catch (ForeignKeyViolationException e) {
    log.error("Cannot delete: still referenced by {}", e.getReferencedTable());
    throw new BusinessException(
        "Cannot delete department: " + e.getReferencedTable() + " records still exist"
    );
}
```

**TypeScript/JavaScript:**
```typescript
try {
  await api.delete('/api/dynamic-data/MY_DB/departments', {
    data: {
      whereClause: 'id = :id',
      whereParams: { id: 1 }
    }
  });
} catch (error) {
  if (error.response?.data?.error === 'FOREIGN_KEY_VIOLATION') {
    const refTable = error.response.data.details.additionalInfo;
    alert(`Cannot delete: Still referenced by ${refTable}`);
  }
}
```

### 3. Not Null Violation

**Request:**
```bash
POST /api/dynamic-data/MY_DB/users
{
  "username": "john",
  "email": null  // email is required
}
```

**Error Response (400):**
```json
{
  "status": 400,
  "error": "NOT_NULL_VIOLATION",
  "message": "Column 'email' cannot be null",
  "details": {
    "tableName": "users",
    "constraintOrColumn": "email",
    "additionalInfo": "Required field"
  },
  "timestamp": "2026-01-28T16:30:00"
}
```

**Client Handling:**
```java
try {
    dataOps.insert("MY_DB", "users", data);
} catch (NotNullViolationException e) {
    log.error("Missing required field: {}", e.getColumnName());
    throw new ValidationException("Field '" + e.getColumnName() + "' is required");
}
```

## Best Practices

### 1. Catch Specific Exceptions

```java
// ✅ Good - Handle each exception type
try {
    dataOps.insert(dbConn, table, data);
} catch (DuplicateKeyException e) {
    // Handle duplicate - maybe upsert instead
    return dataOps.upsert(dbConn, table, data, conflictColumns);
} catch (NotNullViolationException e) {
    // Validation error - return to user
    throw new ValidationException("Missing required field: " + e.getColumnName());
} catch (DataOperationException e) {
    // General database error
    log.error("Database error", e);
    throw new InternalServerException("Database operation failed");
}
```

### 2. Provide User-Friendly Messages

```java
// ❌ Bad - Technical message to user
catch (DuplicateKeyException e) {
    return "ERROR: duplicate key violates unique constraint";
}

// ✅ Good - User-friendly message
catch (DuplicateKeyException e) {
    return "A record with code '" + extractCode(e.getDuplicateKey()) + "' already exists";
}
```

### 3. Log for Debugging

```java
try {
    dataOps.delete(dbConn, table, whereClause, params);
} catch (ForeignKeyViolationException e) {
    log.error("FK violation: table={}, constraint={}, referenced={}",
        e.getTableName(), e.getConstraintName(), e.getReferencedTable());
    throw new BusinessException("Cannot delete: record is in use");
}
```

### 4. Use Try-With-Upsert Pattern

```java
// Try insert first, upsert if duplicate
try {
    return dataOps.insert(dbConn, table, data);
} catch (DuplicateKeyException e) {
    log.info("Record exists, performing upsert: {}", e.getDuplicateKey());
    return dataOps.upsert(dbConn, table, data, List.of("code"));
}
```

### 5. Validate Before Delete

```java
// Check references before deleting
public void deleteDepartment(String dbConn, Integer deptId) {
    long employeeCount = dataOps.count(dbConn, "employees", 
        "department_id = :deptId", Map.of("deptId", deptId));
    
    if (employeeCount > 0) {
        throw new BusinessException(
            "Cannot delete department: " + employeeCount + " employees still assigned"
        );
    }
    
    try {
        dataOps.delete(dbConn, "departments", "id = :id", Map.of("id", deptId));
    } catch (ForeignKeyViolationException e) {
        // Should not happen due to pre-check, but handle anyway
        log.error("Unexpected FK violation", e);
        throw new BusinessException("Cannot delete: department is still in use");
    }
}
```

## Frontend Integration

### React Example

```typescript
import { useState } from 'react';
import { dynamicDataApi } from './api';

function CreateUserForm() {
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (data: any) => {
    try {
      await dynamicDataApi.insert('MY_DB', 'users', data);
      setError(null);
      alert('User created successfully!');
    } catch (err: any) {
      const errorData = err.response?.data;
      
      switch (errorData?.error) {
        case 'DUPLICATE_KEY':
          setError(`User with ${errorData.details.additionalInfo} already exists`);
          break;
        case 'NOT_NULL_VIOLATION':
          setError(`${errorData.details.constraintOrColumn} is required`);
          break;
        case 'FOREIGN_KEY_VIOLATION':
          setError(`Cannot save: Referenced ${errorData.details.additionalInfo} does not exist`);
          break;
        default:
          setError('An unexpected error occurred');
      }
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="error">{error}</div>}
      {/* Form fields */}
    </form>
  );
}
```

### Angular Example

```typescript
import { HttpErrorResponse } from '@angular/common/http';

deleteRecord(id: number) {
  this.dataService.delete('MY_DB', 'departments', { id })
    .subscribe({
      next: () => this.showSuccess('Record deleted'),
      error: (error: HttpErrorResponse) => {
        const errorData = error.error;
        
        if (errorData?.error === 'FOREIGN_KEY_VIOLATION') {
          this.showError(
            `Cannot delete: Still referenced by ${errorData.details.additionalInfo}`
          );
        } else {
          this.showError('Failed to delete record');
        }
      }
    });
}
```

## Testing Error Scenarios

```java
@Test
void testDuplicateKeyHandling() {
    Map<String, Object> data = Map.of("code", "TEST-001", "name", "Test");
    
    // First insert succeeds
    dataOps.insert("MY_DB", "codes", data);
    
    // Second insert throws DuplicateKeyException
    assertThrows(DuplicateKeyException.class, () -> {
        dataOps.insert("MY_DB", "codes", data);
    });
}

@Test
void testForeignKeyViolation() {
    // Create department
    Long deptId = dataOps.insert("MY_DB", "departments", 
        Map.of("name", "Engineering"));
    
    // Create employee in department
    dataOps.insert("MY_DB", "employees", 
        Map.of("name", "John", "department_id", deptId));
    
    // Cannot delete department with employees
    assertThrows(ForeignKeyViolationException.class, () -> {
        dataOps.delete("MY_DB", "departments", "id = :id", Map.of("id", deptId));
    });
}
```

## Summary

✅ **Specific exception types** for each constraint violation  
✅ **Consistent error responses** with detailed information  
✅ **Proper HTTP status codes** (409 for conflicts, 400 for validation)  
✅ **Global exception handler** for REST API  
✅ **User-friendly error messages** extracted from database errors  
✅ **Easy to integrate** in both backend and frontend applications
