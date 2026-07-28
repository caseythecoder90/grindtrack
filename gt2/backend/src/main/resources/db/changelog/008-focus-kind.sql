--liquibase formatted sql

--changeset casey:010-focus-kind
-- A focus session is either personal study or actual work. A 'work' session's minutes fold into
-- work_logs.hours instead of daily_logs.hours (see FocusService). Existing rows default to 'study'.
ALTER TABLE focus_sessions
  ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'study' CHECK (kind IN ('study', 'work'));
--rollback ALTER TABLE focus_sessions DROP COLUMN kind;
