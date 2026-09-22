--liquibase formatted sql

--changeset casey:041-chat-sticker-messages
-- Whether a message was sent as a sticker — from the tray, small and without a bubble — rather
-- than as the picture it is. Until now that was read off the picture (is it in the tray?), so
-- keeping a photo as a sticker shrank the message it first arrived on. The flag is the message's.
ALTER TABLE chat_messages ADD COLUMN sticker BOOLEAN NOT NULL DEFAULT FALSE;

--rollback ALTER TABLE chat_messages DROP COLUMN sticker;
