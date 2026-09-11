--liquibase formatted sql

--changeset casey:026-assistant-week-plan
-- A proposed week of study blocks is a report like any other, so it reuses assistant_reports
-- rather than growing a second table with the same five columns. Only the kind changes; the
-- draft_json holds a WeekPlanDraft instead of a ReviewDraft, and the record of which shape is
-- the kind itself.
--
-- The CHECK is dropped and rebuilt because Postgres cannot extend one in place.
ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check
  CHECK (kind IN ('weekly_review', 'week_plan'));

--rollback ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
--rollback ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check CHECK (kind IN ('weekly_review'));
