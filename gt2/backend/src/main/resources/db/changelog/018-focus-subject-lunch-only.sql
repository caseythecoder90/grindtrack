--liquibase formatted sql

--changeset casey:018-focus-subject-lunch-only
-- Repairs rows written while the subject could leak across a change of kind.
--
-- The subject (plan_item_id, topic) lives in the browser's persisted timer config next to
-- `kind`, so that a reload mid-session cannot detach the minutes from the book they belong
-- to. Switching kind did not clear it, so picking a book for a reading session and later
-- starting a study session filed that study session against the book -- and the sessions
-- table showed the title where the completed/ended-early status should have been.
--
-- FocusService now refuses to set a subject on anything but a lunch kind. This clears the
-- rows that predate that guard. Nothing is lost: a subject on a study or work session was
-- never displayed as anything but wrong, and no rollup has ever read it -- ReadingService
-- filters to READING and REVIEW before grouping, so these rows were invisible to the lunch
-- stats and to the Plan tab's per-item hours.
UPDATE focus_sessions
   SET plan_item_id = NULL,
       topic        = ''
 WHERE kind NOT IN ('reading', 'review')
   AND (plan_item_id IS NOT NULL OR topic <> '');

--rollback --  Irreversible by design: the values were wrong, and there is nothing to restore
--rollback --  them to. Rolling back 018 leaves the repaired rows repaired.
--rollback SELECT 1;
