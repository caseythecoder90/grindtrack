--liquibase formatted sql

--changeset casey:043-calendar-reminder-minutes
-- How far ahead of this block the phone is told, in minutes. NULL is the configured default
-- (grindtrack.calendar.reminder-minutes, ten) for the kinds that are reminded at all and none for
-- a work block; 0 is "do not remind me about this one"; up to a day ahead. Per event rather than
-- per kind because a dentist at nine wants an hour and a study block wants ten minutes.
ALTER TABLE calendar_events ADD COLUMN remind_minutes INTEGER
  CHECK (remind_minutes IS NULL OR (remind_minutes >= 0 AND remind_minutes <= 1440));

--rollback ALTER TABLE calendar_events DROP COLUMN remind_minutes;
