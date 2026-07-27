--liquibase formatted sql

--changeset casey:008-plan-paper
-- Papers & RFCs become their own trackable type, tracked separately from books.
-- The original 004 CHECK enumerated five item types; widen it to include 'paper'.
-- Constraint name is the Postgres default for an inline single-column CHECK
-- ({table}_{column}_check), matching the pattern used in 005.
ALTER TABLE plan_items DROP CONSTRAINT plan_items_item_type_check;
ALTER TABLE plan_items ADD CONSTRAINT plan_items_item_type_check
  CHECK (item_type IN ('milestone', 'cert', 'module', 'book', 'paper', 'project'));
--rollback ALTER TABLE plan_items DROP CONSTRAINT plan_items_item_type_check;
--rollback ALTER TABLE plan_items ADD CONSTRAINT plan_items_item_type_check CHECK (item_type IN ('milestone', 'cert', 'module', 'book', 'project'));