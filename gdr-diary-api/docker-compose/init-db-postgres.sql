CREATE SCHEMA IF NOT EXISTS "dev";
GRANT ALL ON SCHEMA "dev" TO "gdr-diary-api";
ALTER USER "gdr-diary-api" SET search_path TO "dev", public;
-- Add your schema tables here
