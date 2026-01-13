-- =========================================================
-- Sample Data for Dynamic Query Engine
-- Populates metadata tables with example employee domain
-- =========================================================

-- Insert DB Connection
INSERT INTO dqes.cfgtb_dbconn_info (
    conn_code, conn_name, db_vendor, host, port, db_name, db_schema, 
    username, password_enc, password_alg, tenant_code, app_code
) VALUES (
    'LOCAL_DB', 'Local PostgreSQL', 'POSTGRES', 'localhost', 5432, 
    'dqes', 'dqes', 'postgres', 'encrypted_password', 'AES_GCM',
    'SUPPER', 'SUPPER'
) ON CONFLICT DO NOTHING;

-- Insert Operation Metadata
INSERT INTO dqes.qrytb_operation_meta (code, op_symbol, op_label, arity, value_shape, tenant_code, app_code) VALUES
('EQ', '=', 'Equals', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('NE', '!=', 'Not Equals', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('GT', '>', 'Greater Than', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('LT', '<', 'Less Than', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('GTE', '>=', 'Greater Than or Equal', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('LTE', '<=', 'Less Than or Equal', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('IN', 'IN', 'In List', 1, 'ARRAY', 'SUPPER', 'SUPPER'),
('NOT_IN', 'NOT IN', 'Not In List', 1, 'ARRAY', 'SUPPER', 'SUPPER'),
('LIKE', 'LIKE', 'Pattern Match', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('NOT_LIKE', 'NOT LIKE', 'Not Pattern Match', 1, 'SCALAR', 'SUPPER', 'SUPPER'),
('IS_NULL', 'IS NULL', 'Is Null', 0, 'NONE', 'SUPPER', 'SUPPER'),
('IS_NOT_NULL', 'IS NOT NULL', 'Is Not Null', 0, 'NONE', 'SUPPER', 'SUPPER'),
('BETWEEN', 'BETWEEN', 'Between Range', 2, 'RANGE', 'SUPPER', 'SUPPER'),
('EXISTS', 'EXISTS', 'Exists', 0, 'NONE', 'SUPPER', 'SUPPER'),
('NOT_EXISTS', 'NOT EXISTS', 'Not Exists', 0, 'NONE', 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Data Type Metadata
INSERT INTO dqes.qrytb_data_type (code, java_type, pg_cast, tenant_code, app_code) VALUES
('STRING', 'java.lang.String', 'text', 'SUPPER', 'SUPPER'),
('NUMBER', 'java.lang.Integer', 'int4', 'SUPPER', 'SUPPER'),
('DECIMAL', 'java.math.BigDecimal', 'numeric', 'SUPPER', 'SUPPER'),
('DATE', 'java.time.LocalDate', 'date', 'SUPPER', 'SUPPER'),
('DATETIME', 'java.time.LocalDateTime', 'timestamp', 'SUPPER', 'SUPPER'),
('BOOLEAN', 'java.lang.Boolean', 'bool', 'SUPPER', 'SUPPER'),
('UUID', 'java.util.UUID', 'uuid', 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Create sample tables if they don't exist
CREATE TABLE IF NOT EXISTS dqes.employee (
    id SERIAL PRIMARY KEY,
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    employee_name VARCHAR(255) NOT NULL,
    gender_id INT,
    nationality_id INT,
    department_id INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    hire_date DATE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dqes.gender (
    id SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS dqes.nationality (
    id SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS dqes.department (
    id SERIAL PRIMARY KEY,
    dept_code VARCHAR(50) NOT NULL UNIQUE,
    dept_name VARCHAR(255) NOT NULL,
    location_id INT
);

CREATE TABLE IF NOT EXISTS dqes.location (
    id SERIAL PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS dqes.worker (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    employee_id INT REFERENCES dqes.employee(id)
);

-- Insert sample data
INSERT INTO dqes.gender (code, name) VALUES
('M', 'Male'),
('F', 'Female'),
('O', 'Other')
ON CONFLICT DO NOTHING;

INSERT INTO dqes.nationality (code, name) VALUES
('US', 'United States'),
('UK', 'United Kingdom'),
('CA', 'Canada'),
('AU', 'Australia')
ON CONFLICT DO NOTHING;

INSERT INTO dqes.location (city, country) VALUES
('New York', 'USA'),
('London', 'UK'),
('Toronto', 'Canada')
ON CONFLICT DO NOTHING;

INSERT INTO dqes.department (dept_code, dept_name, location_id) VALUES
('IT', 'Information Technology', 1),
('HR', 'Human Resources', 1),
('FIN', 'Finance', 2)
ON CONFLICT DO NOTHING;

INSERT INTO dqes.employee (employee_code, employee_name, gender_id, nationality_id, department_id, hire_date) VALUES
('EMP01', 'John Doe', 1, 1, 1, '2020-01-15'),
('EMP02', 'Jane Smith', 2, 2, 2, '2019-05-20'),
('EMP03', 'Bob Johnson', 1, 1, 1, '2021-03-10')
ON CONFLICT DO NOTHING;

INSERT INTO dqes.worker (code, employee_id) VALUES
('WRK01', 1),
('WRK02', 2),
('WRK03', 3)
ON CONFLICT DO NOTHING;

-- Insert Object Metadata
INSERT INTO dqes.qrytb_object_meta (
    object_code, object_name, db_table, alias_hint, dbconn_id, 
    tenant_code, app_code
) VALUES
('employee', 'Employee', 'dqes.employee', 'emp', 1, 'SUPPER', 'SUPPER'),
('gender', 'Gender', 'dqes.gender', 'gen', 1, 'SUPPER', 'SUPPER'),
('nationality', 'Nationality', 'dqes.nationality', 'nat', 1, 'SUPPER', 'SUPPER'),
('department', 'Department', 'dqes.department', 'dept', 1, 'SUPPER', 'SUPPER'),
('location', 'Location', 'dqes.location', 'loc', 1, 'SUPPER', 'SUPPER'),
('worker', 'Worker', 'dqes.worker', 'wrk', 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Field Metadata for Employee
INSERT INTO dqes.qrytb_field_meta (
    object_code, field_code, field_label, alias_hint, mapping_type, 
    column_name, data_type, dbconn_id, tenant_code, app_code
) VALUES
('employee', 'id', 'Employee ID', 'emp_id', 'COLUMN', 'id', 'NUMBER', 1, 'SUPPER', 'SUPPER'),
('employee', 'employeeCode', 'Employee Code', 'emp_code', 'COLUMN', 'employee_code', 'STRING', 1, 'SUPPER', 'SUPPER'),
('employee', 'employeeName', 'Employee Name', 'emp_name', 'COLUMN', 'employee_name', 'STRING', 1, 'SUPPER', 'SUPPER'),
('employee', 'status', 'Status', 'status', 'COLUMN', 'status', 'STRING', 1, 'SUPPER', 'SUPPER'),
('employee', 'hireDate', 'Hire Date', 'hire_date', 'COLUMN', 'hire_date', 'DATE', 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Field Metadata for Gender
INSERT INTO dqes.qrytb_field_meta (
    object_code, field_code, field_label, alias_hint, mapping_type, 
    column_name, data_type, dbconn_id, tenant_code, app_code
) VALUES
('gender', 'id', 'Gender ID', 'gen_id', 'COLUMN', 'id', 'NUMBER', 1, 'SUPPER', 'SUPPER'),
('gender', 'code', 'Gender Code', 'gen_code', 'COLUMN', 'code', 'STRING', 1, 'SUPPER', 'SUPPER'),
('gender', 'name', 'Gender Name', 'gen_name', 'COLUMN', 'name', 'STRING', 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Field Metadata for Nationality
INSERT INTO dqes.qrytb_field_meta (
    object_code, field_code, field_label, alias_hint, mapping_type, 
    column_name, data_type, dbconn_id, tenant_code, app_code
) VALUES
('nationality', 'id', 'Nationality ID', 'nat_id', 'COLUMN', 'id', 'NUMBER', 1, 'SUPPER', 'SUPPER'),
('nationality', 'code', 'Nationality Code', 'nat_code', 'COLUMN', 'code', 'STRING', 1, 'SUPPER', 'SUPPER'),
('nationality', 'name', 'Nationality Name', 'nat_name', 'COLUMN', 'name', 'STRING', 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Field Metadata for Department
INSERT INTO dqes.qrytb_field_meta (
    object_code, field_code, field_label, alias_hint, mapping_type, 
    column_name, data_type, dbconn_id, tenant_code, app_code
) VALUES
('department', 'id', 'Department ID', 'dept_id', 'COLUMN', 'id', 'NUMBER', 1, 'SUPPER', 'SUPPER'),
('department', 'deptCode', 'Department Code', 'dept_code', 'COLUMN', 'dept_code', 'STRING', 1, 'SUPPER', 'SUPPER'),
('department', 'deptName', 'Department Name', 'dept_name', 'COLUMN', 'dept_name', 'STRING', 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Field Metadata for Worker
INSERT INTO dqes.qrytb_field_meta (
    object_code, field_code, field_label, alias_hint, mapping_type, 
    column_name, data_type, dbconn_id, tenant_code, app_code
) VALUES
('worker', 'id', 'Worker ID', 'wrk_id', 'COLUMN', 'id', 'NUMBER', 1, 'SUPPER', 'SUPPER'),
('worker', 'code', 'Worker Code', 'wrk_code', 'COLUMN', 'code', 'STRING', 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Relation Metadata
INSERT INTO dqes.qrytb_relation_info (
    code, from_object_code, to_object_code, relation_type, join_type, 
    filter_mode, path_weight, dbconn_id, tenant_code, app_code
) VALUES
('employee_to_gender', 'employee', 'gender', 'MANY_TO_ONE', 'LEFT', 'AUTO', 10, 1, 'SUPPER', 'SUPPER'),
('employee_to_nationality', 'employee', 'nationality', 'MANY_TO_ONE', 'LEFT', 'AUTO', 10, 1, 'SUPPER', 'SUPPER'),
('employee_to_department', 'employee', 'department', 'MANY_TO_ONE', 'LEFT', 'AUTO', 10, 1, 'SUPPER', 'SUPPER'),
('department_to_location', 'department', 'location', 'MANY_TO_ONE', 'LEFT', 'AUTO', 10, 1, 'SUPPER', 'SUPPER'),
('worker_to_employee', 'worker', 'employee', 'ONE_TO_ONE', 'INNER', 'AUTO', 10, 1, 'SUPPER', 'SUPPER')
ON CONFLICT DO NOTHING;

-- Insert Relation Join Keys
-- employee -> gender
INSERT INTO dqes.qrytb_relation_join_key (
    relation_id, seq, from_column_name, operator, to_column_name, 
    null_safe, dbconn_id
) 
SELECT r.id, 1, 'gender_id', '=', 'id', false, 1
FROM dqes.qrytb_relation_info r
WHERE r.code = 'employee_to_gender'
AND NOT EXISTS (
    SELECT 1 FROM dqes.qrytb_relation_join_key jk 
    WHERE jk.relation_id = r.id
);

-- employee -> nationality
INSERT INTO dqes.qrytb_relation_join_key (
    relation_id, seq, from_column_name, operator, to_column_name, 
    null_safe, dbconn_id
) 
SELECT r.id, 1, 'nationality_id', '=', 'id', false, 1
FROM dqes.qrytb_relation_info r
WHERE r.code = 'employee_to_nationality'
AND NOT EXISTS (
    SELECT 1 FROM dqes.qrytb_relation_join_key jk 
    WHERE jk.relation_id = r.id
);

-- employee -> department
INSERT INTO dqes.qrytb_relation_join_key (
    relation_id, seq, from_column_name, operator, to_column_name, 
    null_safe, dbconn_id
) 
SELECT r.id, 1, 'department_id', '=', 'id', false, 1
FROM dqes.qrytb_relation_info r
WHERE r.code = 'employee_to_department'
AND NOT EXISTS (
    SELECT 1 FROM dqes.qrytb_relation_join_key jk 
    WHERE jk.relation_id = r.id
);

-- department -> location
INSERT INTO dqes.qrytb_relation_join_key (
    relation_id, seq, from_column_name, operator, to_column_name, 
    null_safe, dbconn_id
) 
SELECT r.id, 1, 'location_id', '=', 'id', false, 1
FROM dqes.qrytb_relation_info r
WHERE r.code = 'department_to_location'
AND NOT EXISTS (
    SELECT 1 FROM dqes.qrytb_relation_join_key jk 
    WHERE jk.relation_id = r.id
);

-- worker -> employee
INSERT INTO dqes.qrytb_relation_join_key (
    relation_id, seq, from_column_name, operator, to_column_name, 
    null_safe, dbconn_id
) 
SELECT r.id, 1, 'employee_id', '=', 'id', false, 1
FROM dqes.qrytb_relation_info r
WHERE r.code = 'worker_to_employee'
AND NOT EXISTS (
    SELECT 1 FROM dqes.qrytb_relation_join_key jk 
    WHERE jk.relation_id = r.id
);

-- Verification queries
SELECT 'Objects:', COUNT(*) FROM dqes.qrytb_object_meta WHERE current_flg = true;
SELECT 'Fields:', COUNT(*) FROM dqes.qrytb_field_meta WHERE current_flg = true;
SELECT 'Relations:', COUNT(*) FROM dqes.qrytb_relation_info WHERE current_flg = true;
SELECT 'Join Keys:', COUNT(*) FROM dqes.qrytb_relation_join_key WHERE current_flg = true;
SELECT 'Operations:', COUNT(*) FROM dqes.qrytb_operation_meta WHERE current_flg = true;
