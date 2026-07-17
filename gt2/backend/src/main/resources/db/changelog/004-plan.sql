--liquibase formatted sql

--changeset casey:006-plan
CREATE TABLE plan_quarters (
  qtr             INT PRIMARY KEY,
  window_label    VARCHAR(40) NOT NULL,
  year_num        INT NOT NULL CHECK (year_num BETWEEN 1 AND 3),
  primary_focus   TEXT NOT NULL DEFAULT '',
  secondary_focus TEXT NOT NULL DEFAULT '',
  career_track    TEXT NOT NULL DEFAULT '',
  deliverables    TEXT NOT NULL DEFAULT ''
);

CREATE TABLE plan_items (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  item_type    VARCHAR(20) NOT NULL CHECK (item_type IN ('milestone', 'cert', 'module', 'book', 'project')),
  title        VARCHAR(300) NOT NULL,
  details      TEXT NOT NULL DEFAULT '',
  target_label VARCHAR(60) NOT NULL DEFAULT '',
  target_date  DATE,
  year_num     INT CHECK (year_num BETWEEN 1 AND 3),
  qtr          INT CHECK (qtr BETWEEN 1 AND 12),
  tier         VARCHAR(30),
  status       VARCHAR(20) NOT NULL DEFAULT 'not_started' CHECK (status IN ('not_started', 'in_progress', 'done')),
  notes        TEXT NOT NULL DEFAULT '',
  completed_at TIMESTAMPTZ,
  sort_order   INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_plan_items_type ON plan_items (item_type);

CREATE TABLE plan_reference (
  sheet        VARCHAR(30) PRIMARY KEY,
  title        VARCHAR(100) NOT NULL,
  content_json TEXT NOT NULL,
  sort_order   INT NOT NULL DEFAULT 0
);
--rollback DROP TABLE plan_reference; DROP TABLE plan_items; DROP TABLE plan_quarters;
