-- =========================================================
-- Example: Multiple Relations Configuration
-- Scenario: emptbEmployee has multiple lookups to comtbCodeList
-- =========================================================

-- Assumption: These objects already exist:
-- - emptbEmployee (object_code: 'emptbEmployee')
-- - comtbCodeList (object_code: 'comtbCodeList')

-- =========================================================
-- 1. Create Relations with join_alias
-- =========================================================

-- Relation 1: Employee -> EmployeeClass Lookup
INSERT INTO dqes.qrytb_relation_info (
  code, 
  from_object_code, 
  to_object_code,
  relation_type,
  join_type,
  join_alias,              -- ⭐ KEY: Unique alias for this relation
  filter_mode,
  is_navigable,
  path_weight,
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
  'employeeClass',         -- ⭐ Use this in queries: employeeClass.code_name
  'AUTO',
  true,
  10,
  1,  -- Replace with actual dbconn_id
  'SUPPER',
  'SUPPER',
  'Join to employee class code lookup'
)
ON CONFLICT (tenant_code, app_code, code) DO UPDATE SET
  join_alias = EXCLUDED.join_alias,
  description = EXCLUDED.description;

-- Relation 2: Employee -> WorkerCategory Lookup
INSERT INTO dqes.qrytb_relation_info (
  code,
  from_object_code,
  to_object_code, 
  relation_type,
  join_type,
  join_alias,              -- ⭐ Different alias
  filter_mode,
  is_navigable,
  path_weight,
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
  'workerCategory',        -- ⭐ Use this in queries: workerCategory.code_name
  'AUTO',
  true,
  10,
  1,  -- Replace with actual dbconn_id
  'SUPPER',
  'SUPPER',
  'Join to worker category code lookup'
)
ON CONFLICT (tenant_code, app_code, code) DO UPDATE SET
  join_alias = EXCLUDED.join_alias,
  description = EXCLUDED.description;

-- Relation 3: Employee -> Gender Lookup
INSERT INTO dqes.qrytb_relation_info (
  code,
  from_object_code,
  to_object_code, 
  relation_type,
  join_type,
  join_alias,
  filter_mode,
  is_navigable,
  path_weight,
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  'EMPLOYEE_TO_GENDER',
  'emptbEmployee',
  'comtbCodeList',
  'MANY_TO_ONE',
  'LEFT',
  'gender',                -- ⭐ gender.code_name
  'AUTO',
  true,
  10,
  1,
  'SUPPER',
  'SUPPER',
  'Join to gender code lookup'
)
ON CONFLICT (tenant_code, app_code, code) DO UPDATE SET
  join_alias = EXCLUDED.join_alias,
  description = EXCLUDED.description;

-- Relation 4: Employee -> Nationality Lookup
INSERT INTO dqes.qrytb_relation_info (
  code,
  from_object_code,
  to_object_code, 
  relation_type,
  join_type,
  join_alias,
  filter_mode,
  is_navigable,
  path_weight,
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  'EMPLOYEE_TO_NATIONALITY',
  'emptbEmployee',
  'comtbCodeList',
  'MANY_TO_ONE',
  'LEFT',
  'nationality',           -- ⭐ nationality.code_name
  'AUTO',
  true,
  10,
  1,
  'SUPPER',
  'SUPPER',
  'Join to nationality code lookup'
)
ON CONFLICT (tenant_code, app_code, code) DO UPDATE SET
  join_alias = EXCLUDED.join_alias,
  description = EXCLUDED.description;

-- =========================================================
-- 2. Create Join Keys for each Relation
-- =========================================================

-- Join keys for employeeClass relation
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id, 
  seq,
  from_column_name,
  operator,
  to_column_name,
  null_safe,
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info 
   WHERE code = 'EMPLOYEE_TO_EMPLOYEE_CLASS' 
     AND tenant_code = 'SUPPER' 
     AND app_code = 'SUPPER'),
  1,
  'employee_class_code',   -- FK column in emptbEmployee
  '=',
  'code',                  -- PK column in comtbCodeList
  false,
  1,
  'SUPPER',
  'SUPPER',
  'Join on employee class code'
)
ON CONFLICT (relation_id, seq) DO UPDATE SET
  from_column_name = EXCLUDED.from_column_name,
  to_column_name = EXCLUDED.to_column_name;

-- Join keys for workerCategory relation
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id,
  seq, 
  from_column_name,
  operator,
  to_column_name,
  null_safe,
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info 
   WHERE code = 'EMPLOYEE_TO_WORKER_CATEGORY'
     AND tenant_code = 'SUPPER' 
     AND app_code = 'SUPPER'),
  1,
  'worker_category_code',  -- Different FK column in emptbEmployee
  '=',
  'code',                  -- Same PK column in comtbCodeList
  false,
  1,
  'SUPPER',
  'SUPPER',
  'Join on worker category code'
)
ON CONFLICT (relation_id, seq) DO UPDATE SET
  from_column_name = EXCLUDED.from_column_name,
  to_column_name = EXCLUDED.to_column_name;

-- Join keys for gender relation
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id,
  seq, 
  from_column_name,
  operator,
  to_column_name,
  null_safe,
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info 
   WHERE code = 'EMPLOYEE_TO_GENDER'
     AND tenant_code = 'SUPPER' 
     AND app_code = 'SUPPER'),
  1,
  'gender_code',
  '=',
  'code',
  false,
  1,
  'SUPPER',
  'SUPPER',
  'Join on gender code'
)
ON CONFLICT (relation_id, seq) DO UPDATE SET
  from_column_name = EXCLUDED.from_column_name,
  to_column_name = EXCLUDED.to_column_name;

-- Join keys for nationality relation
INSERT INTO dqes.qrytb_relation_join_key (
  relation_id,
  seq, 
  from_column_name,
  operator,
  to_column_name,
  null_safe,
  dbconn_id,
  tenant_code,
  app_code,
  description
) VALUES (
  (SELECT id FROM dqes.qrytb_relation_info 
   WHERE code = 'EMPLOYEE_TO_NATIONALITY'
     AND tenant_code = 'SUPPER' 
     AND app_code = 'SUPPER'),
  1,
  'nationality_code',
  '=',
  'code',
  false,
  1,
  'SUPPER',
  'SUPPER',
  'Join on nationality code'
)
ON CONFLICT (relation_id, seq) DO UPDATE SET
  from_column_name = EXCLUDED.from_column_name,
  to_column_name = EXCLUDED.to_column_name;

-- =========================================================
-- 3. Query Examples (use in API request)
-- =========================================================

/*
Example 1: Select from multiple relations
{
  "tenantCode": "SUPPER",
  "appCode": "SUPPER",
  "rootObject": "emptbEmployee",
  "dbconnId": 1,
  "selectFields": [
    "emptbEmployee.employee_code",
    "emptbEmployee.full_name",
    "employeeClass.code_name",
    "workerCategory.code_name",
    "gender.code_name",
    "nationality.code_name"
  ]
}

Expected SQL:
SELECT 
  e0.employee_code,
  e0.full_name,
  employeeClass.code_name,
  workerCategory.code_name,
  gender.code_name,
  nationality.code_name
FROM core.emptb_employee e0
LEFT JOIN core.comtb_code_list employeeClass 
  ON e0.employee_class_code = employeeClass.code
LEFT JOIN core.comtb_code_list workerCategory
  ON e0.worker_category_code = workerCategory.code
LEFT JOIN core.comtb_code_list gender
  ON e0.gender_code = gender.code
LEFT JOIN core.comtb_code_list nationality
  ON e0.nationality_code = nationality.code
*/

/*
Example 2: Filter on multiple relations
{
  "rootObject": "emptbEmployee",
  "selectFields": ["emptbEmployee.employee_code", "emptbEmployee.full_name"],
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
    },
    {
      "logicalOp": "AND",
      "field": "gender.code_value",
      "operator": "EQ",
      "value": "MALE"
    }
  ]
}
*/

/*
Example 3: Complex query with sort
{
  "rootObject": "emptbEmployee",
  "selectFields": [
    "emptbEmployee.employee_code",
    "emptbEmployee.full_name",
    "employeeClass.code_name",
    "gender.code_name"
  ],
  "filters": [
    {
      "field": "nationality.code_value",
      "operator": "EQ",
      "value": "VN"
    }
  ],
  "sorts": [
    {
      "field": "employeeClass.code_name",
      "direction": "ASC"
    },
    {
      "field": "emptbEmployee.full_name",
      "direction": "ASC"
    }
  ],
  "offset": 0,
  "limit": 20
}
*/

-- =========================================================
-- 4. Verification Queries
-- =========================================================

-- Check all relations from emptbEmployee to comtbCodeList
SELECT 
  code,
  from_object_code,
  to_object_code,
  join_alias,
  relation_type,
  join_type,
  description
FROM dqes.qrytb_relation_info
WHERE tenant_code = 'SUPPER'
  AND app_code = 'SUPPER'
  AND from_object_code = 'emptbEmployee'
  AND to_object_code = 'comtbCodeList'
ORDER BY code;

-- Check join keys for each relation
SELECT 
  ri.code as relation_code,
  ri.join_alias,
  rjk.seq,
  rjk.from_column_name,
  rjk.operator,
  rjk.to_column_name,
  rjk.null_safe
FROM dqes.qrytb_relation_info ri
JOIN dqes.qrytb_relation_join_key rjk ON ri.id = rjk.relation_id
WHERE ri.tenant_code = 'SUPPER'
  AND ri.app_code = 'SUPPER'
  AND ri.from_object_code = 'emptbEmployee'
  AND ri.to_object_code = 'comtbCodeList'
ORDER BY ri.code, rjk.seq;

-- Check for duplicate join_alias (should return 0 rows)
SELECT 
  from_object_code,
  join_alias,
  COUNT(*) as cnt
FROM dqes.qrytb_relation_info
WHERE tenant_code = 'SUPPER'
  AND app_code = 'SUPPER'
  AND join_alias IS NOT NULL
GROUP BY from_object_code, join_alias
HAVING COUNT(*) > 1;

-- Find all multiple relations (same from/to pair)
SELECT 
  from_object_code,
  to_object_code,
  COUNT(*) as relation_count,
  STRING_AGG(join_alias, ', ' ORDER BY code) as join_aliases
FROM dqes.qrytb_relation_info
WHERE tenant_code = 'SUPPER'
  AND app_code = 'SUPPER'
GROUP BY from_object_code, to_object_code
HAVING COUNT(*) > 1;
