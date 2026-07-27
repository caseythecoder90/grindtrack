--liquibase formatted sql

--changeset casey:009-work
-- Day-job tracking, separate from the personal study tracking in 002. work_logs is the
-- daily 40h/week accountability + journal; work_skills is the deliberate operational/domain
-- competency checklist. No personal or employer content is seeded here (public repo) — rows
-- are created through the app and live only in the database.
CREATE TABLE work_logs (
  log_date    DATE PRIMARY KEY,
  hours       NUMERIC(4,1) NOT NULL DEFAULT 0 CHECK (hours >= 0 AND hours <= 24),
  categories  VARCHAR(500) NOT NULL DEFAULT '',
  project     VARCHAR(120) NOT NULL DEFAULT '',
  goals       TEXT NOT NULL DEFAULT '',
  did         TEXT NOT NULL DEFAULT '',
  blockers    TEXT NOT NULL DEFAULT '',
  learnings   TEXT NOT NULL DEFAULT '',
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE work_skills (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  category    VARCHAR(40) NOT NULL DEFAULT '',
  detail      TEXT NOT NULL DEFAULT '',
  status      VARCHAR(20) NOT NULL DEFAULT 'not_started'
                CHECK (status IN ('not_started', 'in_progress', 'proficient')),
  notes       TEXT NOT NULL DEFAULT '',
  sort_order  INT NOT NULL DEFAULT 0,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE work_skills; DROP TABLE work_logs;
