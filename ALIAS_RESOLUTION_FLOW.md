# Alias Resolution Flow Diagram

## High-Level Architecture

```
┌─────────────────┐
│ Client Request  │
│ (JSON)          │
└────────┬────────┘
         │
         │ {"rootObject": "employee",
         │  "selectFields": ["employee.name", "workerCategory.name"],
         │  "filters": [{"field": "workerCategory.code", ...}]}
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ DynamicQueryExecutionService                                │
│  - Loads all metadata (ObjectMeta, FieldMeta, RelationInfo)│
│  - Creates QueryContext                                      │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ FieldResolverService.batchResolveFields()                   │
│  - Calls AliasResolverService for each field path           │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ AliasResolverService.resolveFieldPath()                     │
│  1. Parse "workerCategory.name"                             │
│  2. Check if "workerCategory" is:                            │
│     a) Root object alias_hint?                              │
│     b) Relation join_alias?                                 │
│     c) Object alias_hint?                                   │
│  3. Find matching FieldMeta by alias_hint                   │
│  4. Return AliasResolution                                  │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ RelationGraphService.findPath()                             │
│  - Build path from root to target object                    │
│  - Include join_alias in each PathStep                      │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ SqlQueryBuilder.buildQuery()                                │
│  - buildSelectClause(): Use resolved aliases                │
│  - buildJoins(): Generate unique SQL aliases                │
│  - buildWhereClause(): Use resolved field references        │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│ Generated SQL   │
└─────────────────┘
```

## Detailed Alias Resolution Flow

```
Input: "workerCategory.name"
  │
  ├─ Split into ["workerCategory", "name"]
  │
  ▼
Check "workerCategory" against:
  │
  ├─ 1. Root Object (qrytb_object_meta.alias_hint)?
  │    └─ If employee.alias_hint == "workerCategory" → Match!
  │       └─ Return: {objectCode: "EMPLOYEE", isRoot: true}
  │
  ├─ 2. Relation Join Alias (qrytb_relation_info.join_alias)?
  │    └─ If relation.join_alias == "workerCategory" → Match!
  │       └─ Return: {
  │             objectCode: "CODE_LIST",
  │             joinAlias: "workerCategory",
  │             relationCode: "EMPLOYEE_WORKER_CATEGORY",
  │             isRoot: false
  │          }
  │
  └─ 3. Object Alias Hint (qrytb_object_meta.alias_hint)?
       └─ If object.alias_hint == "workerCategory" → Match!
          └─ Return: {objectCode: "CODE_LIST", isRoot: false}

Then lookup field "name":
  │
  └─ Search FieldMeta where:
       - object_code == resolved objectCode
       - alias_hint == "name"
       └─ Found: {field_code: "CODE_NAME", column_name: "name"}

Final Result:
  └─ AliasResolution {
        userAlias: "workerCategory",
        objectCode: "CODE_LIST",
        fieldCode: "CODE_NAME",
        joinAlias: "workerCategory",
        relationCode: "EMPLOYEE_WORKER_CATEGORY"
     }
```

## Runtime Alias Generation

```
QueryContext maintains:
  │
  ├─ objectAliases: Map<String, String>
  │    └─ objectCode → runtime SQL alias
  │       Example: "EMPLOYEE" → "employee0"
  │
  └─ joinInstanceAliases: Map<String, String>
       └─ "objectCode:joinAlias" → runtime SQL alias
          Examples:
            "CODE_LIST:workerCategory" → "workerCategory1"
            "CODE_LIST:employeeClass" → "employeeClass2"
            "EMPLOYEE:manager" → "manager3"

Generation Logic:
  │
  ├─ For root object:
  │    └─ Use: alias_hint + counter
  │       Example: "employee" + 0 = "employee0"
  │
  └─ For joined objects:
       │
       ├─ If join_alias exists:
       │    └─ Use: join_alias + counter
       │       Example: "workerCategory" + 1 = "workerCategory1"
       │
       └─ If no join_alias:
            └─ Use: alias_hint + counter
               Example: "codeList" + 1 = "codeList1"
```

## SQL Generation with Multiple Joins

```
Input Query:
{
  "rootObject": "employee",
  "selectFields": [
    "employee.employeeName",
    "workerCategory.name",
    "employeeClass.code"
  ]
}

Alias Resolution:
  employee.employeeName
    └─ Object: EMPLOYEE (root)
    └─ Field: EMPLOYEE_NAME
    └─ Runtime Alias: employee0

  workerCategory.name
    └─ Object: CODE_LIST (via relation "EMPLOYEE_WORKER_CATEGORY")
    └─ Field: CODE_NAME
    └─ Join Alias: "workerCategory"
    └─ Runtime Alias: workerCategory1

  employeeClass.code
    └─ Object: CODE_LIST (via relation "EMPLOYEE_CLASS")
    └─ Field: CODE
    └─ Join Alias: "employeeClass"
    └─ Runtime Alias: employeeClass2

Generated SQL:
  SELECT 
    employee0.employee_name AS employeeName,
    jsonb_build_object('name', workerCategory1.name) AS workerCategory,
    jsonb_build_object('code', employeeClass2.code) AS employeeClass
  FROM core.emptb_employee employee0
  LEFT JOIN core.comtb_code_list workerCategory1
    ON employee0.worker_category_id = workerCategory1.id
  LEFT JOIN core.comtb_code_list employeeClass2
    ON employee0.employee_class_id = employeeClass2.id
```

## Metadata Structure

```
qrytb_object_meta
┌──────────────┬──────────┬────────────┬────────────┐
│ object_code  │ alias_ht │ db_table   │ dbconn_id  │
├──────────────┼──────────┼────────────┼────────────┤
│ EMPLOYEE     │ employee │ emptb_...  │ 1          │
│ CODE_LIST    │ codeList │ comtb_...  │ 1          │
│ CODE_GROUP   │ codeGrp  │ comtb_...  │ 1          │
└──────────────┴──────────┴────────────┴────────────┘

qrytb_field_meta
┌──────────────┬────────────┬───────────┬─────────────┬────────────┐
│ object_code  │ field_code │ alias_ht  │ column_name │ data_type  │
├──────────────┼────────────┼───────────┼─────────────┼────────────┤
│ EMPLOYEE     │ EMP_NAME   │ name      │ emp_name    │ STRING     │
│ CODE_LIST    │ CODE       │ code      │ code        │ STRING     │
│ CODE_LIST    │ CODE_NAME  │ name      │ name        │ STRING     │
└──────────────┴────────────┴───────────┴─────────────┴────────────┘

qrytb_relation_info
┌──────────────┬────────────┬──────────────┬────────────┬─────────────────┐
│ code         │ from_obj   │ to_obj       │ join_alias │ relation_type   │
├──────────────┼────────────┼──────────────┼────────────┼─────────────────┤
│ EMP_WCAT     │ EMPLOYEE   │ CODE_LIST    │ workerCat  │ MANY_TO_ONE     │
│ EMP_ECLASS   │ EMPLOYEE   │ CODE_LIST    │ empClass   │ MANY_TO_ONE     │
│ EMP_MANAGER  │ EMPLOYEE   │ EMPLOYEE     │ manager    │ MANY_TO_ONE     │
└──────────────┴────────────┴──────────────┴────────────┴─────────────────┘
```

## Caching Strategy

```
Level 1: Repository Cache
  │
  ├─ ObjectMetaRepository
  │    └─ Cache Key: tenantCode_appCode_aliasHint
  │
  ├─ RelationInfoRepository
  │    └─ Cache Key: tenantCode_appCode_joinAlias
  │
  └─ FieldMetaRepository
       └─ Cache Key: tenantCode_appCode_objectCode

Level 2: QueryContext Cache
  │
  ├─ userAliasToObjectCode
  │    └─ Maps user alias to resolved object code
  │
  └─ joinInstanceAliases
       └─ Maps (objectCode + joinAlias) to runtime SQL alias

Benefits:
  ├─ Fast resolution (O(1) lookups)
  ├─ Reduced database queries
  └─ Consistent alias usage within query
```

## Error Handling

```
Unknown Alias Error:
  Input: "unknownAlias.field"
  │
  └─ Check all sources:
       ├─ Root object alias? NO
       ├─ Relation join_alias? NO
       └─ Object alias_hint? NO
  │
  └─ Throw: IllegalArgumentException
       Message: "Unknown alias: unknownAlias. 
                 Not found as join_alias or object alias_hint."

Unknown Field Error:
  Input: "employee.unknownField"
  │
  └─ Found object: EMPLOYEE ✓
  └─ Search fields with alias_hint == "unknownField"
       └─ NO MATCH
  │
  └─ Throw: IllegalArgumentException
       Message: "Field not found with alias: employee.unknownField.
                 Available fields: name, code, email, phone"
```

## Performance Considerations

```
Optimization Techniques:
  │
  ├─ Batch Resolution
  │    └─ Resolve all fields in single pass
  │    └─ Pre-load all metadata
  │    └─ Avoid N+1 query problems
  │
  ├─ Caching
  │    └─ Repository-level caching
  │    └─ QueryContext-level caching
  │    └─ Application-level metadata cache
  │
  ├─ Lazy Loading
  │    └─ Load relation paths only when needed
  │    └─ Defer complex resolution until required
  │
  └─ Index Optimization
       └─ Database indexes on:
            ├─ qrytb_object_meta (alias_hint)
            ├─ qrytb_relation_info (join_alias)
            └─ qrytb_field_meta (object_code, alias_hint)
```
