# Implementation Plan: Alias-Based Query System

## Overview
Change the query system from using `object_code` to using `alias_hint` (from `qrytb_object_meta`) and `join_alias` (from `qrytb_relation_info`) to support:
1. Multiple joins to the same table with different aliases
2. Self-referential relationships (parent-child in same table)
3. User-friendly field references in query requests

## Example Use Case
```sql
-- Employee table references code_list twice
CONSTRAINT emptb_employee_wcategory_fk 
  FOREIGN KEY (worker_category_id, worker_category_code) 
  REFERENCES core.comtb_code_list(id, code),
  
CONSTRAINT emptb_employee_eclass_fk 
  FOREIGN KEY (employee_class_id, employee_class_code) 
  REFERENCES core.comtb_code_list(id, code)
  
-- Employee has self-reference
CONSTRAINT emptb_employee_parent_fk 
  FOREIGN KEY (parent_id) 
  REFERENCES core.emptb_employee(id)
```

## Request Format Change
### Before (using codes)
```json
{
  "rootObject": "CODE_LIST",
  "selectFields": ["CODE_LIST.code", "CODE_GROUP.name"],
  "filters": [{"field": "CODE_GROUP.code", "operatorCode": "EQ", "value": "STATUS"}]
}
```

### After (using alias_hint and join_alias)
```json
{
  "rootObject": "codeList",
  "selectFields": [
    "codeList.code",
    "codeList.name",
    "workerCategory.name",
    "employeeClass.code",
    "manager.employeeName"
  ],
  "filters": [
    {"field": "codeGroup.code", "operatorCode": "EQ", "value": "COMPANY_GROUP"},
    {"field": "workerCategory.code", "operatorCode": "IN", "values": ["W1", "W2"]}
  ]
}
```

## Database Schema Requirements

### qrytb_object_meta
- `alias_hint` (varchar(30), NOT NULL): User-facing alias for the table
- Example: `alias_hint='codeList'` for `COMTB_CODE_LIST` table

### qrytb_relation_info
- `join_alias` (varchar(255), NULL): Unique alias for this specific join
- Used when same table is joined multiple times
- Example: 
  - relation "employee_workerCategory" with `join_alias='workerCategory'`
  - relation "employee_employeeClass" with `join_alias='employeeClass'`

### Field Reference Format
`{join_alias}.{field_alias_hint}` or `{object_alias_hint}.{field_alias_hint}`
- For root object: use `alias_hint` from `qrytb_object_meta`
- For related objects: use `join_alias` from `qrytb_relation_info`
- For field: use `alias_hint` from `qrytb_field_meta`

## Key Changes Required

### 1. Field Path Parsing (FieldResolverService)

#### Current Logic
```java
String objectCode = parts[0];  // Uses object_code
String fieldCode = parts[1];    // Uses field_code
```

#### New Logic
```java
String aliasOrCode = parts[0];  // Can be alias_hint or join_alias
String fieldAlias = parts[1];    // Uses field alias_hint

// Resolve to actual object_code and field_code
ResolvedField field = resolveByAlias(aliasOrCode, fieldAlias, context);
```

### 2. QueryContext Enhancement

Add alias resolution maps:
```java
// Map from join_alias/alias_hint to object_code
private Map<String, String> aliasToObjectCode = new ConcurrentHashMap<>();

// Map from (objectCode + join_alias) to unique runtime alias
private Map<String, String> joinAliasToRuntimeAlias = new ConcurrentHashMap<>();

// Track multiple instances of same object
private Map<String, List<String>> objectToJoinAliases = new ConcurrentHashMap<>();
```

### 3. Relation Path Resolution

Modify `RelationPath.PathStep` to include:
```java
private String joinAlias;  // From qrytb_relation_info
private String targetAliasHint; // From qrytb_object_meta
```

### 4. SQL Generation Updates (SqlQueryBuilder)

#### buildJoinForStep method
```java
// Use join_alias if available, otherwise generate unique alias
String toAlias = step.getJoinAlias() != null 
    ? context.getOrGenerateAliasForJoin(step.getToObject(), step.getJoinAlias())
    : context.getOrGenerateAlias(step.getToObject(), step.getToAlias());
```

#### buildSelectExpression method
Use the correct runtime alias based on join_alias resolution

### 5. Filter Building
Update `buildFilterCondition` to:
1. Parse alias-based field references
2. Map to correct runtime table aliases
3. Support multiple references to same table

## Implementation Steps

### Phase 1: Data Model Updates
- [x] Database schema updated with `join_alias` field
- [ ] Update domain entities (RelationInfo, ObjectMeta, FieldMeta)
- [ ] Add getters/setters for new fields

### Phase 2: Alias Resolution Layer
- [ ] Create `AliasResolver` service
  - Parse user-provided aliases
  - Map to object codes and field codes
  - Track join-specific aliases
- [ ] Update `QueryContext` with alias tracking
- [ ] Modify `FieldResolverService` to use alias resolution

### Phase 3: Relation Graph Updates
- [ ] Update `RelationGraphService` to include `join_alias`
- [ ] Modify `RelationPath.PathStep` to store `join_alias`
- [ ] Update path finding algorithm to handle multiple paths to same table

### Phase 4: SQL Builder Updates
- [ ] Modify `SqlQueryBuilder.buildJoins()` for alias-aware joins
- [ ] Update `buildSelectClause()` to use correct aliases
- [ ] Update `buildFilterCondition()` for alias resolution
- [ ] Add support for self-referential joins

### Phase 5: Testing
- [ ] Test multiple joins to same table
- [ ] Test self-referential relationships
- [ ] Test complex multi-hop paths with aliases
- [ ] Performance testing with large queries

## Example Implementation

### AliasResolver Service
```java
@Service
@RequiredArgsConstructor
public class AliasResolverService {
    
    private final ObjectMetaRepository objectMetaRepository;
    private final RelationInfoRepository relationInfoRepository;
    private final FieldMetaRepository fieldMetaRepository;
    
    public AliasResolution resolveFieldPath(String fieldPath, QueryContext context) {
        String[] parts = fieldPath.split("\\.", 2);
        String userAlias = parts[0];  // Could be alias_hint or join_alias
        String fieldAlias = parts[1];
        
        // Check if root object
        ObjectMeta rootObj = context.getAllObjectMetaMap().get(context.getRootObject());
        if (rootObj.getAliasHint().equals(userAlias)) {
            return resolveRootField(rootObj, fieldAlias);
        }
        
        // Find relation with matching join_alias
        RelationInfo relation = relationInfoRepository
            .findByJoinAlias(context.getTenantCode(), context.getAppCode(), userAlias)
            .orElseThrow(() -> new IllegalArgumentException("Unknown alias: " + userAlias));
        
        // Get target object and field
        ObjectMeta targetObj = context.getAllObjectMetaMap().get(relation.getToObjectCode());
        FieldMeta field = findFieldByAlias(targetObj, fieldAlias);
        
        return AliasResolution.builder()
            .objectCode(targetObj.getObjectCode())
            .fieldCode(field.getFieldCode())
            .joinAlias(relation.getJoinAlias())
            .relationCode(relation.getCode())
            .build();
    }
    
    private FieldMeta findFieldByAlias(ObjectMeta object, String fieldAlias) {
        return object.getFieldMetas().stream()
            .filter(f -> f.getAliasHint().equals(fieldAlias))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Field not found: " + object.getAliasHint() + "." + fieldAlias));
    }
}
```

### Updated QueryContext
```java
public class QueryContext {
    // ... existing fields ...
    
    // New fields for alias resolution
    @Builder.Default
    private Map<String, String> aliasToObjectCode = new ConcurrentHashMap<>();
    
    @Builder.Default  
    private Map<String, String> joinInstanceAliases = new ConcurrentHashMap<>();
    
    public synchronized String getOrGenerateAliasForJoin(
        String objectCode, 
        String joinAlias
    ) {
        String key = objectCode + ":" + joinAlias;
        return joinInstanceAliases.computeIfAbsent(key, k -> {
            ObjectMeta objMeta = allObjectMetaMap.get(objectCode);
            String hint = joinAlias != null ? joinAlias : objMeta.getAliasHint();
            return hint + (aliasCounter++);
        });
    }
}
```

## Migration Strategy

### For Existing Data
1. Run migration script to populate `alias_hint` for all object_meta records
2. Optionally populate `join_alias` for commonly used relations
3. Keep backward compatibility: if `join_alias` is NULL, use object's `alias_hint`

### Migration SQL
```sql
-- Set alias_hint from object_code (convert to camelCase)
UPDATE dqes.qrytb_object_meta 
SET alias_hint = LOWER(SUBSTRING(object_code, 1, 1)) || 
                 SUBSTRING(regexp_replace(object_code, '_(.)', '\U\1', 'g'), 2)
WHERE alias_hint IS NULL OR alias_hint = '';

-- Example results:
-- CODE_LIST -> codeList
-- EMPLOYEE -> employee  
-- CODE_GROUP -> codeGroup
```

## Benefits

1. **User-Friendly**: Queries use intuitive aliases instead of system codes
2. **Flexible**: Same table can be joined multiple times with different aliases
3. **Type-Safe**: Metadata-driven validation of aliases
4. **Maintainable**: Clear separation between physical and logical names
5. **Self-Documenting**: Aliases describe the relationship purpose

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Alias conflicts | Validate uniqueness at metadata level |
| Breaking changes | Provide adapter layer for old format |
| Performance impact | Cache alias resolutions in QueryContext |
| Complex debugging | Enhanced logging with alias mappings |
