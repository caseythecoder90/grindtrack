--liquibase formatted sql

--changeset casey:028-chat-proposals
-- Which week an assistant turn proposed, when it proposed one.
--
-- The draft itself lives where every other drafted week lives, in assistant_reports — this is only
-- the link back, so reopening a conversation shows the same card it showed at the time rather than
-- a reply referring to a proposal that has vanished. Null on every turn that proposed nothing,
-- which is nearly all of them.
ALTER TABLE assistant_messages ADD COLUMN proposed_week_start DATE;

--rollback ALTER TABLE assistant_messages DROP COLUMN proposed_week_start;
