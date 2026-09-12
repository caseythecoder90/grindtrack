--liquibase formatted sql

--changeset casey:029-chat-log-drafts
-- A day's log drafted in conversation, waiting on a click.
--
-- The draft lives in assistant_reports under a third kind, keyed by the day it is about — the
-- week_start column has always been the start of the period a report covers, and a day is a
-- period of one day. The turn that drafted it carries the date the same way a planning turn
-- carries its Monday, so reopening the conversation shows the same card.
ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check
  CHECK (kind IN ('weekly_review', 'week_plan', 'day_log'));

ALTER TABLE assistant_messages ADD COLUMN proposed_log_date DATE;

--rollback ALTER TABLE assistant_messages DROP COLUMN proposed_log_date;
--rollback ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
--rollback ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check CHECK (kind IN ('weekly_review', 'week_plan'));
