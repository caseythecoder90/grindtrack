--liquibase formatted sql

--changeset casey:030-morning-brief
-- The morning brief is a fourth kind of drafted report, one row per day.
ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check
  CHECK (kind IN ('weekly_review', 'week_plan', 'day_log', 'morning_brief'));

--rollback ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
--rollback ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check CHECK (kind IN ('weekly_review', 'week_plan', 'day_log'));
