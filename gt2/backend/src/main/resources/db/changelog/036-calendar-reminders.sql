--liquibase formatted sql

--changeset casey:036-calendar-reminders
-- A block about to start is pushed to the phone once. The scan runs every minute, so it needs to
-- remember what it already sent, and it remembers in the row rather than in memory so a restart
-- does not send the same reminder twice. NULL: not yet; a block whose start has passed unsent
-- stays NULL and is simply skipped.
ALTER TABLE calendar_events ADD COLUMN reminded_at TIMESTAMPTZ;

--rollback ALTER TABLE calendar_events DROP COLUMN reminded_at;
