--A) Chạy trong DB postgres (hoặc bất kỳ DB admin)
-- Create role/user
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dqesowner') THEN
    CREATE ROLE dqesowner LOGIN PASSWORD 'dqesownerdev';
  END IF;
END $$;
ALTER ROLE dqesowner BYPASSRLS;

-- Create database
CREATE DATABASE dqes OWNER dqesowner;

--B) Sau đó connect vào DB dqes và tạo schema dqes
CREATE SCHEMA IF NOT EXISTS dqes AUTHORIZATION dqesowner;

ALTER ROLE dqesowner SET search_path = dqes, public;
GRANT USAGE, CREATE ON SCHEMA dqes TO dqesowner;