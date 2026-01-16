# Multiple Relations Guide - DQES

## Vấn đề

Khi có **nhiều relations từ cùng một object đến cùng một object đích**, ví dụ:
- `emptbEmployee` → `comtbCodeList` (cho employeeClass) 
- `emptbEmployee` → `comtbCodeList` (cho workerCategory)

Cả 2 relations đều join vào table `comtbCodeList` nhưng với mục đích khác nhau (lookup khác nhau).

## Giải pháp: Sử dụng `join_alias`

DQES hỗ trợ field `join_alias` trong table `qrytb_relation_info` để phân biệt các relations này.

---

## 1. Cấu hình Database

### 1.1. Insert Relations với join_alias khác nhau

```sql
-- Relation 1: Employee -> EmployeeClass Lookup
INSERT INTO dqes.qrytb_relation_info (
  code, 
  from_object_code, 
  to_object_code,
  relation_type,
  join_type,
  join_alias,              -- ⭐ Dùng alias để phân biệt
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  'EMPLOYEE_TO_EMPLOYEE_CLASS',
  'emptbEmployee',
  'comtbCodeList',
  'MANY_TO_ONE',
  'LEFT',
  'employeeClass',         -- ⭐ Alias này sẽ dùng trong query
  1,
  'SUPPER',
  'SUPPER',
  'Join to employee class code'
);

-- Relation 2: Employee -> WorkerCategory Lookup
INSERT INTO dqes.qrytb_relation_info (
  code,
  from_object_code,
  to_object_code, 
  relation_type,
  join_type,
  join_alias,              -- ⭐ Alias khác
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  'EMPLOYEE_TO_WORKER_CATEGORY',
  'emptbEmployee',
  'comtbCodeList',
  'MANY_TO_ONE',
  'LEFT',
  'workerCategory',        -- ⭐ Alias khác
  1,
  'SUPPER',
  'SUPPER',
  'Join to worker category code'
);
```

### 1.2. Cấu hình Join Keys

Mỗi relation cần có join keys riêng (join trên columns khác nhau):

```sql
-- Join keys cho employeeClass relation
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id, 
  seq,
  from_column_name,
  operator,
  to_column_name,
  dbconn_id,
  tenant_code,
  app_code
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info 
   WHERE code = 'EMPLOYEE_TO_EMPLOYEE_CLASS' 
     AND tenant_code = 'SUPPER' 
     AND app_code = 'SUPPER'),
  1,
  'employee_class_code',   -- Column trong emptbEmployee
  '=',
  'code',                  -- Column trong comtbCodeList
  1,
  'SUPPER',
  'SUPPER'
);

-- Join keys cho workerCategory relation  
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id,
  seq, 
  from_column_name,
  operator,
  to_column_name,
  dbconn_id,
  tenant_code,
  app_code
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info 
   WHERE code = 'EMPLOYEE_TO_WORKER_CATEGORY'
     AND tenant_code = 'SUPPER' 
     AND app_code = 'SUPPER'),
  1,
  'worker_category_code',  -- Column khác trong emptbEmployee
  '=',
  'code',                  -- Cùng column trong comtbCodeList
  1,
  'SUPPER',
  'SUPPER'
);
```

---

## 2. Cách sử dụng trong Query Request

### ✅ **ĐÚNG**: Dùng `join_alias` thay vì `objectCode`

```json
{
  "tenantCode": "SUPPER",
  "appCode": "SUPPER",
  "rootObject": "emptbEmployee",
  "dbconnId": 1,
  "selectFields": [
    "emptbEmployee.employee_code",
    "emptbEmployee.full_name",
    "employeeClass.code_name",       // ⭐ Dùng join_alias
    "employeeClass.code_value",      // ⭐ Dùng join_alias
    "workerCategory.code_name",      // ⭐ Dùng join_alias
    "workerCategory.code_value"      // ⭐ Dùng join_alias
  ],
  "filters": [
    {
      "field": "employeeClass.code_value",
      "operator": "EQ",
      "value": "CLASS_A"
    },
    {
      "logicalOp": "AND",
      "field": "workerCategory.code_value",
      "operator": "IN",
      "value": ["CAT_WORKER", "CAT_CONTRACTOR"]
    }
  ],
  "sorts": [
    {
      "field": "employeeClass.code_name",
      "direction": "ASC"
    }
  ]
}
```

### ❌ **SAI**: Dùng `objectCode` sẽ gây conflict

```json
{
  "selectFields": [
    "comtbCodeList.code_name",    // ❌ Không biết join nào?
    "comtbCodeList.code_value"    // ❌ Sẽ conflict hoặc lỗi
  ]
}
```

---

## 3. SQL được Generate

System sẽ generate SQL như sau:

```sql
SELECT 
  e0.employee_code,
  e0.full_name,
  employeeClass.code_name,      -- ⭐ Dùng alias từ join_alias
  employeeClass.code_value,
  workerCategory.code_name,     -- ⭐ Dùng alias khác
  workerCategory.code_value
FROM core.emptb_employee e0
LEFT JOIN core.comtb_code_list employeeClass     -- ⭐ Alias 1
  ON e0.employee_class_code = employeeClass.code
LEFT JOIN core.comtb_code_list workerCategory    -- ⭐ Alias 2
  ON e0.worker_category_code = workerCategory.code
WHERE employeeClass.code_value = :param1
  AND workerCategory.code_value IN (:param2)
ORDER BY employeeClass.code_name ASC
```

---

## 4. Implementation Notes (Cho Developers)

### 4.1. Hiện trạng Code

✅ **Đã implement**:
- [RelationInfo.java](src/main/java/com/a4b/dqes/domain/RelationInfo.java): Có field `joinAlias`
- [RelationPath.PathStep](src/main/java/com/a4b/dqes/query/model/RelationPath.java): Có field `toAlias`
- [SqlQueryBuilder.java](src/main/java/com/a4b/dqes/query/builder/SqlQueryBuilder.java): Dùng `step.getToAlias()` để generate alias

### 4.2. Cần Fix/Enhance

⚠️ **Cần kiểm tra**:

**FieldResolverService** cần support lookup relation by `joinAlias`:

```java
// Trong FieldResolverService.resolveField()
String objectCode = parts[0];  // Có thể là joinAlias

// 1. Try lookup as objectCode first
ObjectMeta objectMeta = context.getAllObjectMetaMap().get(objectCode);

// 2. If not found, try lookup as joinAlias
if (objectMeta == null) {
    // Find relation by joinAlias
    Optional<RelationPath> pathOpt = relationGraphService.findPathByAlias(
        context.getTenantCode(),
        context.getAppCode(),
        context.getDbconnId(),
        context.getRootObject(),
        objectCode  // This is joinAlias
    );
    
    if (pathOpt.isPresent()) {
        // Get actual objectCode from relation
        RelationPath path = pathOpt.get();
        String actualObjectCode = path.getToObject();
        objectMeta = context.getAllObjectMetaMap().get(actualObjectCode);
    }
}
```

**RelationGraphService** cần method mới:

```java
/**
 * Find path by join alias instead of objectCode
 * Used for multiple relations to same target object
 */
public Optional<RelationPath> findPathByAlias(
    String tenantCode,
    String appCode, 
    Integer dbconnId,
    String fromObject,
    String joinAlias
) {
    // Find relation with this joinAlias
    Optional<RelationInfo> relation = relationInfoRepository
        .findByJoinAlias(tenantCode, appCode, dbconnId, fromObject, joinAlias);
    
    if (relation.isEmpty()) {
        return Optional.empty();
    }
    
    // Build path with this specific relation
    // ...
}
```

### 4.3. QueryContext Enhancement

Có thể cần cache mapping `joinAlias → objectCode`:

```java
public class QueryContext {
    // Existing fields...
    
    // Map joinAlias to actual objectCode for multi-relation scenarios
    private Map<String, String> aliasToObjectMap = new HashMap<>();
    
    public void registerJoinAlias(String joinAlias, String objectCode) {
        aliasToObjectMap.put(joinAlias, objectCode);
    }
    
    public String resolveObjectCode(String aliasOrCode) {
        return aliasToObjectMap.getOrDefault(aliasOrCode, aliasOrCode);
    }
}
```

---

## 5. Testing Scenarios

### Test Case 1: Select từ multiple relations

```json
{
  "rootObject": "emptbEmployee",
  "selectFields": [
    "emptbEmployee.employee_code",
    "employeeClass.code_name",
    "workerCategory.code_name"
  ]
}
```

**Expected SQL**:
```sql
SELECT e0.employee_code, employeeClass.code_name, workerCategory.code_name
FROM emptb_employee e0
LEFT JOIN comtb_code_list employeeClass ON e0.employee_class_code = employeeClass.code
LEFT JOIN comtb_code_list workerCategory ON e0.worker_category_code = workerCategory.code
```

### Test Case 2: Filter trên multiple relations

```json
{
  "rootObject": "emptbEmployee",
  "selectFields": ["emptbEmployee.employee_code"],
  "filters": [
    {"field": "employeeClass.code_value", "operator": "EQ", "value": "CLASS_A"},
    {"field": "workerCategory.code_value", "operator": "EQ", "value": "CAT_WORKER"}
  ]
}
```

### Test Case 3: Mix select và filter

```json
{
  "rootObject": "emptbEmployee",
  "selectFields": [
    "emptbEmployee.full_name",
    "employeeClass.code_name"
  ],
  "filters": [
    {"field": "workerCategory.code_value", "operator": "IN", "value": ["CAT_1", "CAT_2"]}
  ]
}
```

**Expected**: 2 JOIN vẫn được tạo (1 cho select, 1 cho filter)

---

## 6. Best Practices

### ✅ DO:
- Luôn đặt `join_alias` có ý nghĩa business (như `employeeClass`, `workerCategory`)
- Đảm bảo `join_alias` unique trong scope của `from_object_code`
- Document rõ purpose của mỗi relation trong field `description`

### ❌ DON'T:
- Không dùng chung `join_alias` cho nhiều relations khác nhau
- Không để trống `join_alias` khi có multiple relations đến cùng object
- Không dùng `objectCode` trong query request khi có multiple relations

---

## 7. Troubleshooting

### Lỗi: "No path found from root object to X"

**Nguyên nhân**: Dùng `objectCode` thay vì `joinAlias` khi có multiple relations.

**Giải pháp**: Đổi `"comtbCodeList.field"` → `"employeeClass.field"`

### Lỗi: "Ambiguous relation"  

**Nguyên nhân**: Có 2 relations đến cùng object nhưng không có `join_alias`.

**Giải pháp**: Update relations với `join_alias` unique.

### SQL generate sai alias

**Nguyên nhân**: Code chưa support `joinAlias` properly.

**Giải pháp**: Xem section 4.2 và implement enhancement.

---

## 8. Migration Guide

Nếu đang có code/data hiện tại không dùng `join_alias`:

### 8.1. Identify multiple relations

```sql
SELECT from_object_code, to_object_code, COUNT(*) as cnt
FROM dqes.qrytb_relation_info
GROUP BY from_object_code, to_object_code
HAVING COUNT(*) > 1;
```

### 8.2. Add join_alias

```sql
UPDATE dqes.qrytb_relation_info
SET join_alias = 'employeeClass'
WHERE code = 'EMPLOYEE_TO_EMPLOYEE_CLASS';

UPDATE dqes.qrytb_relation_info  
SET join_alias = 'workerCategory'
WHERE code = 'EMPLOYEE_TO_WORKER_CATEGORY';
```

### 8.3. Update client code

Thay đổi query requests từ:
```json
{"field": "comtbCodeList.code_name"}
```

Thành:
```json
{"field": "employeeClass.code_name"}
```

---

## 9. Examples từ mcrxhrm

### Employee Relations

```sql
-- Employee -> Gender
INSERT INTO dqes.qrytb_relation_info (...) VALUES
  ('EMPLOYEE_TO_GENDER', 'emptbEmployee', 'comtbCodeList', ..., 'gender', ...);

-- Employee -> Nationality  
INSERT INTO dqes.qrytb_relation_info (...) VALUES
  ('EMPLOYEE_TO_NATIONALITY', 'emptbEmployee', 'comtbCodeList', ..., 'nationality', ...);

-- Employee -> MaritalStatus
INSERT INTO dqes.qrytb_relation_info (...) VALUES
  ('EMPLOYEE_TO_MARITAL_STATUS', 'emptbEmployee', 'comtbCodeList', ..., 'maritalStatus', ...);
```

### Query Example

```json
{
  "rootObject": "emptbEmployee",
  "selectFields": [
    "emptbEmployee.full_name",
    "gender.code_name",
    "nationality.code_name",
    "maritalStatus.code_name"
  ],
  "filters": [
    {"field": "gender.code_value", "operator": "EQ", "value": "MALE"},
    {"field": "nationality.code_value", "operator": "EQ", "value": "VN"}
  ]
}
```

---

## Tóm tắt

1. **Config**: Dùng `join_alias` trong `qrytb_relation_info` để phân biệt multiple relations
2. **Usage**: Reference fields qua `joinAlias.fieldCode` thay vì `objectCode.fieldCode`
3. **SQL**: System tự generate correct aliases và JOINs
4. **Code**: Có thể cần enhance FieldResolverService để support joinAlias lookup

---

**Status**: ✅ Schema ready, ⚠️ Code cần verify/enhance

**Related Files**:
- [fulltable_normalized_v2.sql](src/main/resources/sql/fulltable_normalized_v2.sql)
- [RelationInfo.java](src/main/java/com/a4b/dqes/domain/RelationInfo.java)
- [FieldResolverService.java](src/main/java/com/a4b/dqes/query/service/FieldResolverService.java)
- [SqlQueryBuilder.java](src/main/java/com/a4b/dqes/query/builder/SqlQueryBuilder.java)
