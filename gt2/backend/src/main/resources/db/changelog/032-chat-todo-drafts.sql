--liquibase formatted sql

--changeset casey:032-chat-todo-drafts
-- Todos drafted in conversation, waiting on a click: a fifth kind of report, keyed by the day
-- they were drafted on. A day's batch accumulates until it is accepted, and accepting deletes
-- the row, which is what stops a second press from adding the todos twice. The turn that
-- drafted them carries the date like the other two draft kinds do.
ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check
  CHECK (kind IN ('weekly_review', 'week_plan', 'day_log', 'morning_brief', 'todo_batch'));

ALTER TABLE assistant_messages ADD COLUMN proposed_todos_date DATE;

--rollback ALTER TABLE assistant_messages DROP COLUMN proposed_todos_date;
--rollback ALTER TABLE assistant_reports DROP CONSTRAINT assistant_reports_kind_check;
--rollback ALTER TABLE assistant_reports ADD CONSTRAINT assistant_reports_kind_check CHECK (kind IN ('weekly_review', 'week_plan', 'day_log', 'morning_brief'));
