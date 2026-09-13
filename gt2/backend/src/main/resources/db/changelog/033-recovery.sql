--liquibase formatted sql

--changeset casey:033-recovery
-- The recovery tab. Every table here starts empty: the books are imported by the person who
-- owns them, the journal is written by hand, and the Bible is seeded from a public-domain
-- resource on first start. Nothing personal and nothing copyrighted lives in a migration.

-- One imported book per slot. Replacing a book replaces the row and everything under it.
CREATE TABLE recovery_texts (
  id              BIGSERIAL PRIMARY KEY,
  slot            VARCHAR(16)  NOT NULL UNIQUE CHECK (slot IN ('big_book', 'reflection', 'meditation')),
  title           VARCHAR(120) NOT NULL,
  imported_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  paragraph_count INT          NOT NULL DEFAULT 0,
  word_count      INT          NOT NULL DEFAULT 0
);

-- A book read in order: one row per paragraph, in reading order.
CREATE TABLE recovery_paragraphs (
  id            BIGSERIAL PRIMARY KEY,
  text_id       BIGINT       NOT NULL REFERENCES recovery_texts(id) ON DELETE CASCADE,
  chapter_no    INT          NOT NULL,
  chapter_title VARCHAR(120) NOT NULL,
  seq           INT          NOT NULL,
  body          TEXT         NOT NULL,
  words         INT          NOT NULL,
  UNIQUE (text_id, seq)
);

-- A book read by date: one row per calendar day.
CREATE TABLE recovery_daily_entries (
  id      BIGSERIAL PRIMARY KEY,
  text_id BIGINT       NOT NULL REFERENCES recovery_texts(id) ON DELETE CASCADE,
  month   INT          NOT NULL CHECK (month BETWEEN 1 AND 12),
  day     INT          NOT NULL CHECK (day BETWEEN 1 AND 31),
  title   VARCHAR(200) NOT NULL,
  body    TEXT         NOT NULL,
  UNIQUE (text_id, month, day)
);

-- One row of settings and cursors. The row is created on first read.
CREATE TABLE recovery_settings (
  id                 SMALLINT PRIMARY KEY CHECK (id = 1),
  read_cursor        INT  NOT NULL DEFAULT 0,
  read_minutes       INT  NOT NULL DEFAULT 5,
  read_throughs      INT  NOT NULL DEFAULT 0,
  meditation_minutes INT  NOT NULL DEFAULT 10,
  bible_plan_start   DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE recovery_journal (
  id         BIGSERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  body       TEXT        NOT NULL,
  spoken     BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX recovery_journal_created_idx ON recovery_journal (created_at DESC);

CREATE TABLE recovery_sessions (
  id         BIGSERIAL PRIMARY KEY,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  minutes    INT         NOT NULL,
  completed  BOOLEAN     NOT NULL DEFAULT TRUE
);

-- The day's marks: read today (and which paragraphs, so the part stays on screen after the
-- cursor has moved on), meditated today.
CREATE TABLE recovery_days (
  day       DATE    PRIMARY KEY,
  read_done BOOLEAN NOT NULL DEFAULT FALSE,
  read_from INT,
  read_to   INT,
  meditated BOOLEAN NOT NULL DEFAULT FALSE
);

-- The Bible, one verse per row, seeded from backend/src/main/resources/recovery/*.jsonl.gz on
-- first start. `para` is true when the verse opens a paragraph, which is what the daily
-- passages are cut on. `book_ord` is the canonical order, 1..66.
CREATE TABLE bible_verses (
  id       BIGSERIAL PRIMARY KEY,
  book     VARCHAR(3) NOT NULL,
  book_ord INT        NOT NULL,
  chapter  INT        NOT NULL,
  verse    INT        NOT NULL,
  para     BOOLEAN    NOT NULL,
  text     TEXT       NOT NULL,
  UNIQUE (book, chapter, verse)
);
CREATE INDEX bible_verses_order_idx ON bible_verses (book_ord, chapter, verse);

--rollback DROP TABLE bible_verses;
--rollback DROP TABLE recovery_days;
--rollback DROP TABLE recovery_sessions;
--rollback DROP TABLE recovery_journal;
--rollback DROP TABLE recovery_settings;
--rollback DROP TABLE recovery_daily_entries;
--rollback DROP TABLE recovery_paragraphs;
--rollback DROP TABLE recovery_texts;
