--liquibase formatted sql

--changeset casey:039-chat-messages
-- One room, two people. There is no conversation table because there is one conversation, the
-- owner's and the partner's; a third person would mean rooms, and that is a migration for that
-- day. client_id is the phone's own id for a message, so a retry after a dropped connection is
-- the same message and not a second one. An unsent message keeps its row with the body cleared,
-- so both sides still agree on what sits where.
CREATE TABLE chat_messages (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sender_id  BIGINT NOT NULL REFERENCES users (id),
  body       TEXT NOT NULL,
  client_id  UUID NOT NULL UNIQUE,
  sent_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_chat_messages_sender ON chat_messages (sender_id, id);
--rollback DROP TABLE chat_messages;

--changeset casey:039-chat-reactions
-- One row per person per emoji per message; the same emoji twice from the same person is once.
CREATE TABLE chat_reactions (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  message_id BIGINT NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
  user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  emoji      VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (message_id, user_id, emoji)
);
--rollback DROP TABLE chat_reactions;

--changeset casey:039-chat-cursors
-- How far each person has got: everything up to delivered_id reached one of their devices, and
-- everything up to read_id was on their screen. Two numbers per person, not two rows per message.
CREATE TABLE chat_cursors (
  user_id      BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  delivered_id BIGINT NOT NULL DEFAULT 0,
  read_id      BIGINT NOT NULL DEFAULT 0,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE chat_cursors;
