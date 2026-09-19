--liquibase formatted sql

--changeset casey:040-chat-media
-- A photo or clip in the bucket: the bytes are there, the row is what the app knows about them.
-- poster_key is a small JPEG made on the phone before the upload — a photo's thumbnail or a
-- video's frame — so the thread lays out and loads without touching the full object. sticker is
-- the tray: a picture either of you kept to send again, as many times as you like.
CREATE TABLE chat_media (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  owner_id      BIGINT NOT NULL REFERENCES users (id),
  kind          VARCHAR(16) NOT NULL,
  object_key    TEXT NOT NULL UNIQUE,
  poster_key    TEXT,
  content_type  VARCHAR(100) NOT NULL,
  bytes         BIGINT NOT NULL,
  width         INT,
  height        INT,
  duration_ms   INT,
  sticker       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE chat_media;

--changeset casey:040-chat-messages-media
-- A message carries at most one upload. An upload is on one message, unless it is a sticker,
-- which is on as many as it was sent. The body may be empty when there is a picture; unsending
-- clears both.
ALTER TABLE chat_messages ADD COLUMN media_id BIGINT REFERENCES chat_media (id) ON DELETE SET NULL;
CREATE INDEX idx_chat_messages_media ON chat_messages (media_id) WHERE media_id IS NOT NULL;
--rollback DROP INDEX idx_chat_messages_media; ALTER TABLE chat_messages DROP COLUMN media_id;
