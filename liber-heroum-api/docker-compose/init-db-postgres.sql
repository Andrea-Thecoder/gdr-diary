CREATE SCHEMA IF NOT EXISTS "dev";
GRANT ALL ON SCHEMA "dev" TO "liber-heroum-api";
ALTER USER "liber-heroum-api" SET search_path TO "dev", public;
-- Add your schema tables here
