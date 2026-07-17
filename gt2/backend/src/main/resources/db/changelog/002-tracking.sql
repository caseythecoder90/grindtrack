--liquibase formatted sql

--changeset casey:003-daily-logs
CREATE TABLE daily_logs (
  log_date   DATE PRIMARY KEY,
  hours      NUMERIC(4,1) NOT NULL DEFAULT 0 CHECK (hours >= 0 AND hours <= 24),
  categories VARCHAR(500) NOT NULL DEFAULT '',
  focus      TEXT NOT NULL DEFAULT '',
  did        TEXT NOT NULL DEFAULT '',
  wins       TEXT NOT NULL DEFAULT '',
  blockers   TEXT NOT NULL DEFAULT '',
  energy     INT CHECK (energy BETWEEN 1 AND 5),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE daily_logs;

--changeset casey:004-weekly-reviews
CREATE TABLE weekly_reviews (
  week_start  DATE PRIMARY KEY,
  summary     TEXT NOT NULL DEFAULT '',
  wins        TEXT NOT NULL DEFAULT '',
  blockers    TEXT NOT NULL DEFAULT '',
  adjustments TEXT NOT NULL DEFAULT '',
  next_focus  TEXT NOT NULL DEFAULT '',
  on_track    BOOLEAN,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE weekly_reviews;
