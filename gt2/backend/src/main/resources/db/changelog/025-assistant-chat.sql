--liquibase formatted sql

--changeset casey:025-assistant-chat
-- Conversations with the assistant. Text turns only, deliberately: the tool calls a reply made
-- along the way are working state inside one request, not history worth replaying — the answer
-- they produced is in the assistant's text. Keeping turns as plain text makes the replay cheap
-- and the transcript readable straight out of the table.
--
-- Token counts ride on each assistant turn for the same reason they ride on reports: the status
-- endpoint prices the month from what actually happened, not from an estimate.
CREATE TABLE assistant_conversations (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title           VARCHAR(120) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,
  last_message_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE assistant_messages (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES assistant_conversations (id) ON DELETE CASCADE,
  role            VARCHAR(12) NOT NULL CHECK (role IN ('user', 'assistant')),
  content         TEXT NOT NULL,
  input_tokens    BIGINT NOT NULL DEFAULT 0,
  output_tokens   BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_assistant_messages_conversation ON assistant_messages (conversation_id);

--rollback DROP TABLE assistant_messages; DROP TABLE assistant_conversations;
