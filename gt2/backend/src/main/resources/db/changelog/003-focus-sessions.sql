--liquibase formatted sql

--changeset casey:005-focus-sessions
CREATE TABLE focus_sessions (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  session_date     DATE NOT NULL,
  started_at       TIMESTAMPTZ NOT NULL,
  duration_minutes INT NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
  completed        BOOLEAN NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_focus_sessions_date ON focus_sessions (session_date);
--rollback DROP TABLE focus_sessions;
