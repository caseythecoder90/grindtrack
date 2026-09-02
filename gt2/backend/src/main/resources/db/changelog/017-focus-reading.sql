--liquibase formatted sql

--changeset casey:017-focus-reading
-- The lunch slot. Two kinds join study/work: 'reading' (books, papers, RFCs) and 'review'
-- (reading your own code). Both fold into daily_logs.hours exactly as 'study' does, because both
-- are study; they are separate constants so a lunch streak can be counted apart from the 6-8am
-- block, which would otherwise paper over a skipped lunch every time a long evening was logged.
ALTER TABLE focus_sessions DROP CONSTRAINT IF EXISTS focus_sessions_kind_check;
ALTER TABLE focus_sessions
  ADD CONSTRAINT focus_sessions_kind_check CHECK (kind IN ('study', 'work', 'reading', 'review'));

-- What the session went into. A bare 40 minutes is not motivating; "9.2h over 14 sessions into
-- DDIA, 3 chapters left" is, and that needs the session to name its subject.
--
-- Both columns, deliberately. plan_item_id is the live link, and it survives a re-import now that
-- PlanService updates matched rows in place instead of deleting them. topic is the label snapshot
-- taken at write time, so history stays readable even if the workbook later drops or renames the
-- item -- and it is the only subject a code-review session has, since a repo is not a plan item.
ALTER TABLE focus_sessions
  ADD COLUMN plan_item_id BIGINT REFERENCES plan_items (id) ON DELETE SET NULL,
  ADD COLUMN topic        VARCHAR(200) NOT NULL DEFAULT '',
  ADD COLUMN takeaway     TEXT NOT NULL DEFAULT '';

CREATE INDEX idx_focus_sessions_plan_item ON focus_sessions (plan_item_id);
-- The lunch rollups all filter by kind before anything else.
CREATE INDEX idx_focus_sessions_kind_date ON focus_sessions (kind, session_date);

--rollback DROP INDEX idx_focus_sessions_kind_date;
--rollback DROP INDEX idx_focus_sessions_plan_item;
--rollback ALTER TABLE focus_sessions DROP COLUMN takeaway, DROP COLUMN topic, DROP COLUMN plan_item_id;
--rollback ALTER TABLE focus_sessions DROP CONSTRAINT focus_sessions_kind_check;
--rollback ALTER TABLE focus_sessions ADD CONSTRAINT focus_sessions_kind_check CHECK (kind IN ('study', 'work'));
