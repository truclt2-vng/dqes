-- =========================================================
-- fulltable_normalized_v2.sql
-- Dynamic Query Engine Schema (PostgreSQL) - FULL (rewritten)
--
-- Schema: dqes
-- Goals:
--  - NamedParameterJdbcTemplate-friendly (typed ops, safe params)
--  - Multi-hop graph joins (relation graph + join keys + path cache)
--  - NO raw join_on SQL (structured join predicates)
--  - Safe expressions via allowlist (expr_code + template sandbox)
--  - FK integrity across the metadata graph
--
-- Default tenant/app in template: SUPPER/SUPPER (change as needed)
-- =========================================================

-- --------------------------
-- 0) Prerequisites
-- --------------------------
DROP SCHEMA IF EXISTS dqes CASCADE;
CREATE SCHEMA IF NOT EXISTS dqes;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- --------------------------
-- 1) System Columns Template
-- --------------------------
DROP TABLE IF EXISTS dqes._sys_cols_template CASCADE;

CREATE TABLE dqes._sys_cols_template (
  tenant_code varchar(40) NOT NULL DEFAULT 'SUPPER',
  app_code    varchar(50) NOT NULL DEFAULT 'SUPPER',

  agg_id uuid NOT NULL DEFAULT uuid_generate_v4(),

  record_order    int4 NOT NULL DEFAULT 1,

  record_status varchar(1) NOT NULL DEFAULT 'O'
    CHECK (record_status IN ('O','C','D')),

  auth_status varchar(1) NOT NULL DEFAULT 'A'
    CHECK (auth_status IN ('U','A')),

  maker_id     varchar(50) NULL,
  maker_date   timestamptz NULL,
  checker_id   varchar(50) NULL,
  checker_date timestamptz NULL,
  update_id    varchar(50) NULL,
  update_date  timestamptz NULL,

  mod_no      numeric NOT NULL DEFAULT 0,
  create_date timestamptz NOT NULL DEFAULT now(),

  fts_string_value text NULL,
  fts_value tsvector GENERATED ALWAYS AS (
    to_tsvector('simple'::regconfig, trim(coalesce(fts_string_value,'')) || ' ')
  ) STORED
);

-- --------------------------
-- 2) DB Connection Info
-- --------------------------
DROP TABLE IF EXISTS dqes.cfgtb_dbconn_info CASCADE;

CREATE TABLE dqes.cfgtb_dbconn_info (
  id serial4 NOT NULL,

  conn_code varchar(100) NOT NULL,
  conn_name varchar(255) NOT NULL,

  db_vendor varchar(30) NOT NULL DEFAULT 'POSTGRES',
  host varchar(255) NOT NULL,
  port int4 NOT NULL,
  db_name varchar(255) NOT NULL,
  db_schema varchar(255) NULL,

  username varchar(255) NOT NULL,

  -- store ciphertext only (encrypt at app layer)
  password_enc text NOT NULL,
  password_alg varchar(50) NULL DEFAULT 'AES_GCM',

  -- SSL options
  ssl_enabled bool NULL DEFAULT false,
  ssl_mode varchar(30) NULL,
  jdbc_params jsonb NULL,

  -- meta scan options
  include_schemas jsonb NULL,
  include_tables jsonb NULL,
  exclude_tables jsonb NULL,

  description varchar(2000) NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT cfgtb_dbconn_info_pk PRIMARY KEY (id),
  CONSTRAINT cfgtb_dbconn_info_uk UNIQUE (tenant_code, app_code, conn_code)
);

CREATE INDEX IF NOT EXISTS idx_cfgtb_dbconn_info_fts
  ON dqes.cfgtb_dbconn_info USING gin (fts_value);

CREATE INDEX IF NOT EXISTS idx_cfgtb_dbconn_info_lookup
  ON dqes.cfgtb_dbconn_info USING btree (tenant_code, app_code, conn_code);

-- --------------------------
-- 3) Operation Meta
-- --------------------------
DROP TABLE IF EXISTS dqes.qrytb_operation_meta CASCADE;

CREATE TABLE dqes.qrytb_operation_meta (
  id serial4 NOT NULL,

  code        varchar(100) NOT NULL,    -- EQ, NE, IN, BETWEEN, IS_NULL...
  op_symbol   varchar(20)  NULL,
  op_label    varchar(100) NOT NULL,
  arity       int4 NOT NULL DEFAULT 1 CHECK (arity IN (0,1,2)),
  value_shape varchar(20) NOT NULL DEFAULT 'SCALAR'
    CHECK (value_shape IN ('SCALAR','ARRAY','RANGE','NONE')),
  description varchar(2000) NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_operation_meta_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_operation_meta_uk UNIQUE (tenant_code, app_code, code)
);

CREATE INDEX IF NOT EXISTS idx_qrytb_operation_meta_fts
  ON dqes.qrytb_operation_meta USING gin (fts_value);

-- --------------------------
-- 4) Data Type Meta
-- --------------------------
DROP TABLE IF EXISTS dqes.qrytb_data_type CASCADE;

CREATE TABLE dqes.qrytb_data_type (
  id serial4 NOT NULL,

  code        varchar(100) NOT NULL,      -- STRING/NUMBER/DATE/UUID/JSON/TSVECTOR...
  java_type   varchar(200) NULL,          -- helpful for binding
  pg_cast     varchar(50)  NULL,          -- optional hint: uuid/date/jsonb/tsvector...
  description varchar(2000) NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_data_type_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_data_type_uk UNIQUE (tenant_code, app_code, code)
);

CREATE INDEX IF NOT EXISTS idx_qrytb_data_type_fts
  ON dqes.qrytb_data_type USING gin (fts_value);

-- M:N mapping between DataType and Operation
DROP TABLE IF EXISTS dqes.qrytb_data_type_op CASCADE;

CREATE TABLE dqes.qrytb_data_type_op (
  id serial4 NOT NULL,

  data_type_code varchar(100) NOT NULL,
  op_code        varchar(100) NOT NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_data_type_op_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_data_type_op_uk UNIQUE (tenant_code, app_code, data_type_code, op_code),

  CONSTRAINT qrytb_data_type_op_dtype_fk
    FOREIGN KEY (tenant_code, app_code, data_type_code)
    REFERENCES dqes.qrytb_data_type(tenant_code, app_code, code),

  CONSTRAINT qrytb_data_type_op_op_fk
    FOREIGN KEY (tenant_code, app_code, op_code)
    REFERENCES dqes.qrytb_operation_meta(tenant_code, app_code, code)
);

CREATE INDEX IF NOT EXISTS idx_qrytb_data_type_op_dtype
  ON dqes.qrytb_data_type_op (tenant_code, app_code, data_type_code);

-- --------------------------
-- 5) Expression Allowlist (safe sandbox)
-- --------------------------
DROP TABLE IF EXISTS dqes.qrytb_expr_allowlist CASCADE;

CREATE TABLE dqes.qrytb_expr_allowlist (
  id serial4 NOT NULL,

  expr_code varchar(100) NOT NULL,   -- LOWER, CONCAT2, COALESCE2...
  expr_type varchar(20) NOT NULL DEFAULT 'TEMPLATE'
    CHECK (expr_type IN ('TEMPLATE','FUNCTION')),

  -- TEMPLATE: placeholders {0}, {1}, ...
  sql_template text NOT NULL,

  allow_in_select bool NOT NULL DEFAULT true,
  allow_in_filter bool NOT NULL DEFAULT true,
  allow_in_sort   bool NOT NULL DEFAULT false,

  min_args int4 NOT NULL DEFAULT 1 CHECK (min_args >= 0),
  max_args int4 NOT NULL DEFAULT 1 CHECK (max_args >= min_args),

  -- optional signature help
  args_spec jsonb NULL,               -- [{"pos":0,"kind":"FIELD"}, ...]
  return_data_type varchar(100) NULL, -- optional FK to qrytb_data_type
  description varchar(2000) NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_expr_allowlist_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_expr_allowlist_uk UNIQUE (tenant_code, app_code, expr_code),

  CONSTRAINT qrytb_expr_allowlist_return_dtype_fk
    FOREIGN KEY (tenant_code, app_code, return_data_type)
    REFERENCES dqes.qrytb_data_type(tenant_code, app_code, code)
);

CREATE INDEX IF NOT EXISTS idx_qrytb_expr_allowlist_lookup
  ON dqes.qrytb_expr_allowlist (tenant_code, app_code, expr_code);

CREATE INDEX IF NOT EXISTS idx_qrytb_expr_allowlist_fts
  ON dqes.qrytb_expr_allowlist USING gin (fts_value);

-- --------------------------
-- 6) Object Meta
-- --------------------------
DROP TABLE IF EXISTS dqes.qrytb_object_meta CASCADE;

CREATE TABLE dqes.qrytb_object_meta (
  id serial4 NOT NULL,

  object_code varchar(100) NOT NULL,     -- EMPLOYEE/DEPARTMENT/...
  object_name varchar(255) NOT NULL,

  db_table    varchar(255) NOT NULL,     -- schema.table
  alias_hint  varchar(30)  NOT NULL,         -- hint only; runtime alias should be allocated by engine

  dbconn_id int4 NOT NULL,
  description varchar(2000) NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_object_meta_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_object_meta_uk UNIQUE (tenant_code, app_code, object_code),

  CONSTRAINT qrytb_object_meta_dbconn_fk
    FOREIGN KEY (dbconn_id)
    REFERENCES dqes.cfgtb_dbconn_info(id)
);

CREATE INDEX IF NOT EXISTS idx_qrytb_object_meta_lookup
  ON dqes.qrytb_object_meta (tenant_code, app_code, object_code);

CREATE INDEX IF NOT EXISTS idx_qrytb_object_meta_fts
  ON dqes.qrytb_object_meta USING gin (fts_value);

-- --------------------------
-- 7) Relation Meta (Graph edges) - structured join keys (NO raw join_on)
-- --------------------------
DROP TABLE IF EXISTS dqes.qrytb_relation_info CASCADE;

CREATE TABLE dqes.qrytb_relation_info (
  id serial4 NOT NULL,

  code varchar(150) NOT NULL,                -- unique per tenant/app
  from_object_code varchar(100) NOT NULL,
  to_object_code   varchar(100) NOT NULL,

  relation_type varchar(30) NOT NULL
    CHECK (relation_type IN ('MANY_TO_ONE','ONE_TO_MANY','ONE_TO_ONE','MANY_TO_MANY')),

  join_type varchar(10) NOT NULL DEFAULT 'LEFT'
    CHECK (join_type IN ('INNER','LEFT')),
  
  join_alias varchar(255),

  -- filtering strategy when object is used in WHERE only (not selected/sorted)
  filter_mode varchar(20) NOT NULL DEFAULT 'AUTO'
    CHECK (filter_mode IN ('AUTO','JOIN_ONLY','EXISTS_PREFERRED','EXISTS_ONLY')),

  is_required bool NULL DEFAULT false,
  is_navigable bool NULL DEFAULT true,

  path_weight int4 NOT NULL DEFAULT 10 CHECK (path_weight >= 0),

  -- optional dependency ordering
  relation_props jsonb NULL,
  description varchar(2000) NULL,

  dbconn_id int4 NOT NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_relation_info_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_relation_info_uk UNIQUE (tenant_code, app_code, code),

  CONSTRAINT qrytb_relation_from_object_fk
    FOREIGN KEY (tenant_code, app_code, from_object_code)
    REFERENCES dqes.qrytb_object_meta(tenant_code, app_code, object_code),

  CONSTRAINT qrytb_relation_to_object_fk
    FOREIGN KEY (tenant_code, app_code, to_object_code)
    REFERENCES dqes.qrytb_object_meta(tenant_code, app_code, object_code),
  
  CONSTRAINT qrytb_relation_info_dbconn_fk
    FOREIGN KEY (dbconn_id)
    REFERENCES dqes.cfgtb_dbconn_info(id)
);

CREATE INDEX IF NOT EXISTS idx_qrytb_relation_info_fts
  ON dqes.qrytb_relation_info USING gin (fts_value);

CREATE INDEX IF NOT EXISTS idx_qrytb_relation_info_from
  ON dqes.qrytb_relation_info (tenant_code, app_code, from_object_code);

CREATE INDEX IF NOT EXISTS idx_qrytb_relation_info_to
  ON dqes.qrytb_relation_info (tenant_code, app_code, to_object_code);

CREATE INDEX IF NOT EXISTS idx_qrytb_relation_info_pair
  ON dqes.qrytb_relation_info (tenant_code, app_code, from_object_code, to_object_code);

-- Join keys (predicates) for each relation
DROP TABLE IF EXISTS dqes.qrytb_relation_join_key CASCADE;

CREATE TABLE dqes.qrytb_relation_join_key (
  id serial4 NOT NULL,

  relation_id int4 NOT NULL,
  seq int4 NOT NULL DEFAULT 1 CHECK (seq >= 1),

  -- Store column names only; runtime aliases are applied by engine
  from_column_name varchar(255) NOT NULL,
  operator varchar(10) NOT NULL DEFAULT '='
    CHECK (operator IN ('=','!=','<','>','<=','>=')),
  to_column_name   varchar(255) NOT NULL,

  -- Postgres null-safe equality: IS NOT DISTINCT FROM
  null_safe bool NOT NULL DEFAULT false,

  description varchar(2000) NULL,

  dbconn_id int4 NOT NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_relation_join_key_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_relation_join_key_uk UNIQUE (relation_id, seq),

  CONSTRAINT qrytb_relation_join_key_dbconn_fk
    FOREIGN KEY (dbconn_id)
    REFERENCES dqes.cfgtb_dbconn_info(id),

  CONSTRAINT qrytb_relation_join_key_rel_fk
    FOREIGN KEY (relation_id)
    REFERENCES dqes.qrytb_relation_info(id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_qrytb_relation_join_key_rel
  ON dqes.qrytb_relation_join_key (relation_id, seq);


-- --------------------------
-- 7) Field Meta (Normalized mapping + safe expr codes)
-- --------------------------

CREATE TABLE dqes.qrytb_relation_join_condition (
  id serial4 NOT NULL,

  relation_id int4 NOT NULL,
  seq int4 NOT NULL DEFAULT 1,

  -- column on joined table
  column_name varchar(255) NOT NULL,

  operator varchar(20) NOT NULL,

  -- value kind
  value_type varchar(20) NOT NULL CHECK (value_type IN ('CONST','PARAM','EXPR')),

  -- actual value (stringified, typed at runtime)
  value_literal text NULL,

  -- optional for PARAM
  param_name varchar(100) NULL,

  description varchar(2000) NULL,

  dbconn_id int4 NOT NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_relation_join_condition_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_relation_join_condition_uk UNIQUE (relation_id, seq),

  CONSTRAINT qrytb_relation_join_condition_rel_fk
    FOREIGN KEY (relation_id)
    REFERENCES dqes.qrytb_relation_info(id)
    ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_qrytb_relation_join_condition_rel
  ON dqes.qrytb_relation_join_condition (relation_id, seq);

-- --------------------------
-- 8) Field Meta (Normalized mapping + safe expr codes)
-- --------------------------
DROP TABLE IF EXISTS dqes.qrytb_field_meta CASCADE;

CREATE TABLE dqes.qrytb_field_meta (
  id serial4 NOT NULL,
  dbconn_id int4 NOT NULL,
  object_code  varchar(100) NOT NULL,
  field_code   varchar(100) NOT NULL,
  field_label  varchar(255) NOT NULL,
  alias_hint  varchar(30)  NOT NULL,

  is_primary bool NULL DEFAULT false,

  mapping_type varchar(10) NOT NULL DEFAULT 'COLUMN'
    CHECK (mapping_type IN ('COLUMN','EXPR')),

  -- COLUMN mapping (physical)
  column_name  varchar(255) NULL,   -- column name only

  -- EXPR mapping (sandbox preferred via allowlist)
  select_expr_code varchar(100) NULL,  -- preferred: allowlist code
  filter_expr_code varchar(100) NULL,  -- preferred: allowlist code (optional override)
  expr_args jsonb NULL,                -- arguments for template rendering (engine-defined)

  -- legacy raw SQL (NOT recommended; keep only if you must)
  select_expr  text NULL,
  filter_expr  text NULL,
  expr_lang    varchar(20) NOT NULL DEFAULT 'SQL'
    CHECK (expr_lang IN ('SQL','TEMPLATE')),

  data_type    varchar(100) NOT NULL,

  not_null     bool NULL DEFAULT false,
  ui_control   jsonb NULL,
  is_default   bool NULL DEFAULT false,

  default_select bool NULL DEFAULT false,
  allow_select bool NULL DEFAULT false,
  allow_filter bool NULL DEFAULT true,
  allow_sort   bool NULL DEFAULT true,

  roles_allowed jsonb NULL,
  description varchar(2000) NULL,

  LIKE dqes._sys_cols_template INCLUDING DEFAULTS INCLUDING generated,

  CONSTRAINT qrytb_field_meta_pk PRIMARY KEY (id),
  CONSTRAINT qrytb_field_meta_uk UNIQUE (tenant_code, app_code, object_code, field_code),

  CONSTRAINT qrytb_field_meta_object_fk
    FOREIGN KEY (tenant_code, app_code, object_code)
    REFERENCES dqes.qrytb_object_meta(tenant_code, app_code, object_code),

  CONSTRAINT qrytb_field_meta_dtype_fk
    FOREIGN KEY (tenant_code, app_code, data_type)
    REFERENCES dqes.qrytb_data_type(tenant_code, app_code, code),

  CONSTRAINT qrytb_field_meta_select_expr_code_fk
    FOREIGN KEY (tenant_code, app_code, select_expr_code)
    REFERENCES dqes.qrytb_expr_allowlist(tenant_code, app_code, expr_code),

  CONSTRAINT qrytb_field_meta_filter_expr_code_fk
    FOREIGN KEY (tenant_code, app_code, filter_expr_code)
    REFERENCES dqes.qrytb_expr_allowlist(tenant_code, app_code, expr_code),

  -- Mapping consistency:
  --  - COLUMN: must have column_name, must not have expr_code nor select_expr
  --  - EXPR: must not have column_name, and must have (select_expr_code OR select_expr)
  CONSTRAINT qrytb_field_meta_ck_mapping
    CHECK (
      (mapping_type = 'COLUMN'
        AND column_name IS NOT NULL
        AND select_expr_code IS NULL
        AND select_expr IS NULL
      )
      OR
      (mapping_type = 'EXPR'
        AND column_name IS NULL
        AND (select_expr_code IS NOT NULL OR select_expr IS NOT NULL)
      )
    )
);

CREATE INDEX IF NOT EXISTS idx_qrytb_field_meta_lookup
  ON dqes.qrytb_field_meta (tenant_code, app_code, object_code);

CREATE INDEX IF NOT EXISTS idx_qrytb_field_meta_expr_code
  ON dqes.qrytb_field_meta (tenant_code, app_code, select_expr_code, filter_expr_code);

CREATE INDEX IF NOT EXISTS idx_qrytb_field_meta_fts
  ON dqes.qrytb_field_meta USING gin (fts_value);

-- =========================================================
-- 11) SEED DATA (basic defaults) - includes JSON & TSVECTOR
-- =========================================================

-- ---- Data Types ----
INSERT INTO dqes.qrytb_data_type (code, java_type, pg_cast, description, tenant_code, app_code)
VALUES
  ('STRING',    'java.lang.String',         'text',        'Text/string',         'SUPPER','SUPPER'),
  ('NUMBER',    'java.math.BigDecimal',     'numeric',     'Numeric/decimal',     'SUPPER','SUPPER'),
  ('INT',       'java.lang.Long',           'bigint',      'Integer',             'SUPPER','SUPPER'),
  ('BOOLEAN',   'java.lang.Boolean',        'boolean',     'Boolean',             'SUPPER','SUPPER'),
  ('DATE',      'java.time.LocalDate',      'date',        'Date',                'SUPPER','SUPPER'),
  ('TIMESTAMP', 'java.time.OffsetDateTime', 'timestamptz', 'Timestamp with tz',   'SUPPER','SUPPER'),
  ('UUID',      'java.util.UUID',           'uuid',        'UUID',                'SUPPER','SUPPER'),
  ('JSON',      'com.fasterxml.jackson.databind.JsonNode', 'jsonb',   'JSON/JSONB document', 'SUPPER','SUPPER'),
  ('TSVECTOR',  'java.lang.String',         'tsvector',    'PostgreSQL tsvector', 'SUPPER','SUPPER')
ON CONFLICT (tenant_code, app_code, code) DO NOTHING;

-- ---- Operations ----
INSERT INTO dqes.qrytb_operation_meta
(code, op_symbol, op_label, arity, value_shape, description, tenant_code, app_code)
VALUES
  ('EQ',          '=',          'Equals',                 1, 'SCALAR', 'field = value',             'SUPPER','SUPPER'),
  ('NE',          '!=',         'Not equals',             1, 'SCALAR', 'field != value',            'SUPPER','SUPPER'),
  ('GT',          '>',          'Greater than',           1, 'SCALAR', 'field > value',             'SUPPER','SUPPER'),
  ('GE',          '>=',         'Greater or equal',       1, 'SCALAR', 'field >= value',            'SUPPER','SUPPER'),
  ('LT',          '<',          'Less than',              1, 'SCALAR', 'field < value',             'SUPPER','SUPPER'),
  ('LE',          '<=',         'Less or equal',          1, 'SCALAR', 'field <= value',            'SUPPER','SUPPER'),
  ('IN',          'IN',         'In list',                1, 'ARRAY',  'field IN (values)',         'SUPPER','SUPPER'),
  ('NOT_IN',      'NOT IN',     'Not in list',            1, 'ARRAY',  'field NOT IN (values)',     'SUPPER','SUPPER'),
  ('BETWEEN',     'BETWEEN',    'Between',                2, 'RANGE',  'field BETWEEN a AND b',     'SUPPER','SUPPER'),
  ('LIKE',        'LIKE',       'Like',                   1, 'SCALAR', 'field LIKE pattern',        'SUPPER','SUPPER'),
  ('ILIKE',       'ILIKE',      'Case-insensitive like',  1, 'SCALAR', 'field ILIKE pattern',       'SUPPER','SUPPER'),
  ('IS_NULL',     'IS NULL',    'Is null',                0, 'NONE',   'field IS NULL',             'SUPPER','SUPPER'),
  ('IS_NOT_NULL', 'IS NOT NULL','Is not null',            0, 'NONE',   'field IS NOT NULL',         'SUPPER','SUPPER')
ON CONFLICT (tenant_code, app_code, code) DO NOTHING;

-- ---- Type-Op mappings (common defaults) ----
-- STRING
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'STRING', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('IN'),('NOT_IN'),('LIKE'),('ILIKE'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- NUMBER
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'NUMBER', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('GT'),('GE'),('LT'),('LE'),('IN'),('NOT_IN'),('BETWEEN'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- INT
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'INT', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('GT'),('GE'),('LT'),('LE'),('IN'),('NOT_IN'),('BETWEEN'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- DATE
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'DATE', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('GT'),('GE'),('LT'),('LE'),('BETWEEN'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- TIMESTAMP
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'TIMESTAMP', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('GT'),('GE'),('LT'),('LE'),('BETWEEN'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- BOOLEAN
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'BOOLEAN', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- UUID
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'UUID', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('IN'),('NOT_IN'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- JSON (practical subset; you can extend with JSON_CONTAINS/JSON_PATH ops later)
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'JSON', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('EQ'),('NE'),('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- TSVECTOR (usually needs @@ + to_tsquery / plainto_tsquery; keep minimal by default)
INSERT INTO dqes.qrytb_data_type_op (data_type_code, op_code, tenant_code, app_code)
SELECT 'TSVECTOR', x.op, 'SUPPER','SUPPER'
FROM (VALUES ('IS_NULL'),('IS_NOT_NULL')) x(op)
ON CONFLICT (tenant_code, app_code, data_type_code, op_code) DO NOTHING;

-- ---- Expression allowlist defaults ----
INSERT INTO dqes.qrytb_expr_allowlist
(expr_code, expr_type, sql_template,
 allow_in_select, allow_in_filter, allow_in_sort,
 min_args, max_args, args_spec, return_data_type, description,
 tenant_code, app_code)
VALUES
  ('LOWER', 'TEMPLATE', 'lower({0})',
   true, true, true,
   1, 1, '[{"pos":0,"kind":"FIELD"}]'::jsonb, 'STRING', 'lower(field)',
   'SUPPER','SUPPER'),

  ('UPPER', 'TEMPLATE', 'upper({0})',
   true, true, true,
   1, 1, '[{"pos":0,"kind":"FIELD"}]'::jsonb, 'STRING', 'upper(field)',
   'SUPPER','SUPPER'),

  ('TRIM', 'TEMPLATE', 'btrim({0})',
   true, true, true,
   1, 1, '[{"pos":0,"kind":"FIELD"}]'::jsonb, 'STRING', 'trim(field)',
   'SUPPER','SUPPER'),

  ('COALESCE2', 'TEMPLATE', 'coalesce({0}, {1})',
   true, true, true,
   2, 2, '[{"pos":0,"kind":"FIELD"},{"pos":1,"kind":"CONST_OR_FIELD"}]'::jsonb, 'STRING', 'coalesce(a,b)',
   'SUPPER','SUPPER'),

  ('CONCAT2', 'TEMPLATE', 'concat({0}, {1})',
   true, false, false,
   2, 2, '[{"pos":0,"kind":"FIELD_OR_CONST"},{"pos":1,"kind":"FIELD_OR_CONST"}]'::jsonb, 'STRING',
   'concat(a,b) - prefer SELECT only',
   'SUPPER','SUPPER'),

  ('DATE_TRUNC_DAY', 'TEMPLATE', 'date_trunc(''day'', {0})',
   true, true, true,
   1, 1, '[{"pos":0,"kind":"FIELD"}]'::jsonb, 'TIMESTAMP', 'date_trunc(day, ts)',
   'SUPPER','SUPPER'),

  -- JSON helpers (SELECT/FILTER), sandboxed:
  ('JSON_TEXT', 'TEMPLATE', '{0} #>> {1}',
   true, true, true,
   2, 2, '[{"pos":0,"kind":"FIELD"},{"pos":1,"kind":"CONST"}]'::jsonb, 'STRING',
   'jsonb #>> path (path should be const like ''{a,b}'')',
   'SUPPER','SUPPER')
ON CONFLICT (tenant_code, app_code, expr_code) DO NOTHING;

-- =========================================================
-- 12) Optional: refresh path cache example call
-- =========================================================
-- CALL dqes.refresh_qry_object_paths('SUPPER','SUPPER', 6);

-- =========================================================
-- END
-- =========================================================
