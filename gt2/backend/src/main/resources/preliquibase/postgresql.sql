-- Preliquibase runs before Liquibase. Its one job here: make sure the schema
-- exists so Liquibase has somewhere to create its own tables (DATABASECHANGELOG)
-- and ours. Same pattern as work: preliquibase = schema, Liquibase = objects.
CREATE SCHEMA IF NOT EXISTS grindtrack;
