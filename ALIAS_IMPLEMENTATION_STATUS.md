# Implementation Summary: Alias-Based Query System

## Implemented Components

### 1. AliasResolverService ✅
**Location**: `src/main/java/com/a4b/dqes/query/service/AliasResolverService.java`

**Purpose**: Resolves user-provided aliases (alias_hint, join_alias) to actual object codes and field codes

**Key Methods**:
- `resolveFieldPath(String fieldPath, QueryContext context)`: Main entry point
- `resolveRootField()`: Handle root object fields
- `resolveRelationField()`: Handle fields from specific relations (with join_alias)
- `resolveObjectField()`: Handle fields by object alias_hint
- `findFieldByAlias()`: Find field metadata by alias_hint

**Example Usage**:
```java
// Resolve "workerCategory.name" where:
// - workerCategory is join_alias from qrytb_relation_info
// - name is field alias_hint from qrytb_field_meta

AliasResolution resolution = aliasResolverService.resolveFieldPath(
    "workerCategory.name", 
    context
);

// Returns:
// - objectCode: "CODE_LIST"
// - fieldCode: "CODE_NAME" 
// - joinAlias: "workerCategory"
// - relationCode: "EMPLOYEE_WORKER_CATEGORY"
```

### 2. AliasResolution Model ✅
**Location**: `src/main/java/com/a4b/dqes/query/model/AliasResolution.java`

**Purpose**: Holds the result of alias resolution

**Fields**:
- `userAlias`: Original alias provided by user
- `objectCode`: Resolved object code
- `fieldCode`: Resolved field code
- `fieldMeta`: Full field metadata
- `objectMeta`: Full object metadata
- `joinAlias`: Join alias from relation (if applicable)
- `relationCode`: Relation code (if applicable)
- `relationInfo`: Full relation metadata
- `isRootObject`: Whether this is the root object

### 3. QueryContext Updates ✅
**Location**: `src/main/java/com/a4b/dqes/query/model/QueryContext.java`

**New Fields**:
```java
// Maps user alias to object code
private Map<String, String> userAliasToObjectCode;

// Maps objectCode:joinAlias to unique runtime SQL alias
private Map<String, String> joinInstanceAliases;
```

**New Methods**:
```java
// Generate unique alias for specific join instance
String getOrGenerateAliasForJoin(String objectCode, String joinAlias, String aliasHint)

// Register user alias mapping
void registerUserAlias(String userAlias, String objectCode)

// Lookup object code by user alias
String getObjectCodeFromAlias(String userAlias)
```

**Key Features**:
- Supports multiple joins to same table with different aliases
- Thread-safe with synchronized methods
- Automatic alias generation with counter

### 4. RelationPath.PathStep Update ✅
**Location**: `src/main/java/com/a4b/dqes/query/model/RelationPath.java`

**New Field**:
```java
private String joinAlias;  // From qrytb_relation_info
```

**Purpose**: Store join_alias in path steps for multi-hop join resolution

### 5. Repository Updates ✅
**Location**: `src/main/java/com/a4b/dqes/repository/jpa/RelationInfoRepository.java`

**New Method**:
```java
Optional<RelationInfo> findByTenantCodeAndAppCodeAndJoinAlias(
    String tenantCode, String appCode, String joinAlias
);
```

**Existing Methods** (already in place):
- `findByTenantCodeAndAppCodeAndAliasHint()` in ObjectMetaRepository

## Remaining Work (TODO)

### 1. Update FieldResolverService ⏳
**File**: `src/main/java/com/a4b/dqes/query/service/FieldResolverService.java`

**Changes Needed**:
```java
// BEFORE:
String[] parts = fieldPath.split("\\.", 2);
String objectCode = parts[0];  // Assumes object_code
String fieldCode = parts[1];   // Assumes field_code

// AFTER:
String[] parts = fieldPath.split("\\.", 2);
String userAlias = parts[0];  // Can be alias_hint or join_alias
String fieldAlias = parts[1]; // Field alias_hint

// Resolve using AliasResolverService
AliasResolution resolution = aliasResolverService.resolveFieldPath(fieldPath, context);
String objectCode = resolution.getObjectCode();
String fieldCode = resolution.getFieldCode();
```

### 2. Update RelationGraphService ⏳
**File**: `src/main/java/com/a4b/dqes/query/service/RelationGraphService.java`

**Changes Needed**:
- Include `join_alias` when building PathStep objects
- Support finding multiple paths to same object with different join aliases
- Update findPath() to consider join_alias parameter

```java
// Update PathStep creation
RelationPath.PathStep step = RelationPath.PathStep.builder()
    .fromObject(relation.getFromObjectCode())
    .toObject(relation.getToObjectCode())
    .joinAlias(relation.getJoinAlias())  // NEW
    .relationCode(relation.getCode())
    .joinType(relation.getJoinType())
    .weight(relation.getPathWeight())
    .build();
```

### 3. Update SqlQueryBuilder ⏳
**File**: `src/main/java/com/a4b/dqes/query/builder/SqlQueryBuilder.java`

**Changes Needed**:

#### buildJoinForStep():
```java
// BEFORE:
String toAlias = context.getOrGenerateAlias(
    step.getToObject(), 
    step.getToAlias()
);

// AFTER:
String toAlias = context.getOrGenerateAliasForJoin(
    step.getToObject(),
    step.getJoinAlias(),  // NEW
    step.getToAlias()
);
```

#### buildFilterCondition():
```java
// BEFORE:
String[] parts = filter.getField().split("\\.", 2);
String objectCode = parts[0];
String fieldCode = parts[1];

// AFTER:
// Use AliasResolverService to resolve the alias
AliasResolution resolution = aliasResolverService.resolveFieldPath(
    filter.getField(), 
    context
);

String objectCode = resolution.getObjectCode();
String fieldCode = resolution.getFieldCode();
String runtimeAlias = context.getObjectAliases().get(resolution.getUniqueKey());
```

### 4. Update Domain Entities ⏳
Ensure domain entities have the new fields:

**RelationInfo.java**:
```java
@Column(name = "join_alias")
private String joinAlias;
```

**ObjectMeta.java**:
```java
@Column(name = "alias_hint", nullable = false)
private String aliasHint;
```

**FieldMeta.java**:
```java
@Column(name = "alias_hint", nullable = false)
private String aliasHint;
```

## Usage Examples

### Example 1: Simple Query with Aliases
```json
{
  "tenantCode": "SUPPER",
  "appCode": "SUPPER",
  "dbconnId": 1,
  "rootObject": "codeList",
  "selectFields": [
    "codeList.code",
    "codeList.name",
    "codeGroup.name"
  ],
  "filters": [
    {
      "field": "codeGroup.code",
      "operatorCode": "EQ",
      "value": "COMPANY_GROUP"
    }
  ]
}
```

**Generated SQL**:
```sql
SELECT 
  codeList0.code AS code,
  codeList0.name AS name,
  jsonb_build_object('name', codeGroup1.name) AS codeGroup
FROM core.comtb_code_list codeList0
LEFT JOIN core.comtb_code_group codeGroup1 
  ON codeList0.code_group_id = codeGroup1.id
WHERE codeGroup1.code = :param_0
```

### Example 2: Multiple Joins to Same Table
```json
{
  "rootObject": "employee",
  "selectFields": [
    "employee.employeeName",
    "workerCategory.name",
    "employeeClass.code",
    "manager.employeeName"
  ],
  "filters": [
    {
      "field": "workerCategory.code",
      "operatorCode": "IN",
      "values": ["W1", "W2"]
    }
  ]
}
```

**Database Setup**:
```sql
-- qrytb_relation_info
INSERT INTO qrytb_relation_info (
  code, from_object_code, to_object_code, 
  relation_type, join_alias
) VALUES
  ('EMPLOYEE_WORKER_CATEGORY', 'EMPLOYEE', 'CODE_LIST', 
   'MANY_TO_ONE', 'workerCategory'),
  ('EMPLOYEE_CLASS', 'EMPLOYEE', 'CODE_LIST', 
   'MANY_TO_ONE', 'employeeClass'),
  ('EMPLOYEE_MANAGER', 'EMPLOYEE', 'EMPLOYEE', 
   'MANY_TO_ONE', 'manager');
```

**Generated SQL**:
```sql
SELECT 
  employee0.employee_name AS employeeName,
  jsonb_build_object('name', workerCategory1.name) AS workerCategory,
  jsonb_build_object('code', employeeClass2.code) AS employeeClass,
  jsonb_build_object('employeeName', manager3.employee_name) AS manager
FROM core.emptb_employee employee0
LEFT JOIN core.comtb_code_list workerCategory1 
  ON employee0.worker_category_id = workerCategory1.id
LEFT JOIN core.comtb_code_list employeeClass2 
  ON employee0.employee_class_id = employeeClass2.id
LEFT JOIN core.emptb_employee manager3 
  ON employee0.parent_id = manager3.id
WHERE workerCategory1.code IN (:param_0)
```

## Testing Checklist

- [ ] Test simple query with alias_hint
- [ ] Test multiple joins to same table
- [ ] Test self-referential join (parent-child)
- [ ] Test nested object selection
- [ ] Test filter on aliased fields
- [ ] Test sorting on aliased fields
- [ ] Test error handling for unknown aliases
- [ ] Test performance with many aliases
- [ ] Test cache effectiveness
- [ ] Test concurrent queries with aliases

## Migration Guide

### Step 1: Update Database
```sql
-- Ensure join_alias column exists
ALTER TABLE dqes.qrytb_relation_info 
ADD COLUMN IF NOT EXISTS join_alias varchar(255);

-- Set join_alias for important relations
UPDATE dqes.qrytb_relation_info 
SET join_alias = 'workerCategory'
WHERE code = 'EMPLOYEE_WORKER_CATEGORY';

UPDATE dqes.qrytb_relation_info 
SET join_alias = 'employeeClass'
WHERE code = 'EMPLOYEE_CLASS';
```

### Step 2: Deploy Code
1. Deploy new service classes (AliasResolverService, AliasResolution)
2. Deploy updated repositories
3. Deploy updated models (QueryContext, RelationPath)
4. Update existing services (FieldResolverService, SqlQueryBuilder, RelationGraphService)

### Step 3: Update Client Code
Update API requests to use aliases instead of codes:
```javascript
// OLD
{
  "rootObject": "CODE_LIST",
  "selectFields": ["CODE_LIST.CODE", "CODE_GROUP.NAME"]
}

// NEW
{
  "rootObject": "codeList",
  "selectFields": ["codeList.code", "codeGroup.name"]
}
```

## Benefits

1. **User-Friendly**: Intuitive, readable field references
2. **Flexible**: Multiple joins to same table with different purposes
3. **Maintainable**: Clear separation of concerns
4. **Type-Safe**: Validated through metadata
5. **Performant**: Cached resolutions, efficient lookups

## Next Steps

1. Complete FieldResolverService updates
2. Complete RelationGraphService updates
3. Complete SqlQueryBuilder updates
4. Add comprehensive unit tests
5. Add integration tests
6. Update API documentation
7. Create migration scripts for existing data
