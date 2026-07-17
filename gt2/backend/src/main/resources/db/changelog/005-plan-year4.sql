--liquibase formatted sql

--changeset casey:007-plan-year4
-- Extend the plan to a 4th year (16 quarters). The original 004 CHECKs capped
-- year_num<=3 and qtr<=12; widen them. Constraint names are Postgres defaults
-- for inline single-column CHECKs ({table}_{column}_check).
ALTER TABLE plan_quarters DROP CONSTRAINT plan_quarters_year_num_check;
ALTER TABLE plan_quarters ADD CONSTRAINT plan_quarters_year_num_check CHECK (year_num BETWEEN 1 AND 4);
ALTER TABLE plan_items DROP CONSTRAINT plan_items_year_num_check;
ALTER TABLE plan_items ADD CONSTRAINT plan_items_year_num_check CHECK (year_num BETWEEN 1 AND 4);
ALTER TABLE plan_items DROP CONSTRAINT plan_items_qtr_check;
ALTER TABLE plan_items ADD CONSTRAINT plan_items_qtr_check CHECK (qtr BETWEEN 1 AND 16);
--rollback ALTER TABLE plan_items DROP CONSTRAINT plan_items_qtr_check;
--rollback ALTER TABLE plan_items ADD CONSTRAINT plan_items_qtr_check CHECK (qtr BETWEEN 1 AND 12);
--rollback ALTER TABLE plan_items DROP CONSTRAINT plan_items_year_num_check;
--rollback ALTER TABLE plan_items ADD CONSTRAINT plan_items_year_num_check CHECK (year_num BETWEEN 1 AND 3);
--rollback ALTER TABLE plan_quarters DROP CONSTRAINT plan_quarters_year_num_check;
--rollback ALTER TABLE plan_quarters ADD CONSTRAINT plan_quarters_year_num_check CHECK (year_num BETWEEN 1 AND 3);
