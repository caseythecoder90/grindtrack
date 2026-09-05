--liquibase formatted sql

--changeset casey:019-plan-year5
-- Extend the plan to a 5th year (20 quarters). 005 widened the original CHECKs to
-- 4 years / 16 quarters; widen again. Constraint names are Postgres defaults for
-- inline single-column CHECKs ({table}_{column}_check), as in 005.
ALTER TABLE plan_quarters DROP CONSTRAINT plan_quarters_year_num_check;
ALTER TABLE plan_quarters ADD CONSTRAINT plan_quarters_year_num_check CHECK (year_num BETWEEN 1 AND 5);
ALTER TABLE plan_items DROP CONSTRAINT plan_items_year_num_check;
ALTER TABLE plan_items ADD CONSTRAINT plan_items_year_num_check CHECK (year_num BETWEEN 1 AND 5);
ALTER TABLE plan_items DROP CONSTRAINT plan_items_qtr_check;
ALTER TABLE plan_items ADD CONSTRAINT plan_items_qtr_check CHECK (qtr BETWEEN 1 AND 20);
--rollback ALTER TABLE plan_items DROP CONSTRAINT plan_items_qtr_check;
--rollback ALTER TABLE plan_items ADD CONSTRAINT plan_items_qtr_check CHECK (qtr BETWEEN 1 AND 16);
--rollback ALTER TABLE plan_items DROP CONSTRAINT plan_items_year_num_check;
--rollback ALTER TABLE plan_items ADD CONSTRAINT plan_items_year_num_check CHECK (year_num BETWEEN 1 AND 4);
--rollback ALTER TABLE plan_quarters DROP CONSTRAINT plan_quarters_year_num_check;
--rollback ALTER TABLE plan_quarters ADD CONSTRAINT plan_quarters_year_num_check CHECK (year_num BETWEEN 1 AND 4);
