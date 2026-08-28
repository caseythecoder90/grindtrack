--liquibase formatted sql

--changeset casey:015-relationship
-- The relationship tab. Four tables: what happened, what you meant to do, what is coming up, and
-- what you are reading.
--
-- Nothing is seeded. This is the most personal data in the app and the repo is public, so no real
-- content ships in SQL -- same rule finance_category_rules follows.
--
-- One design decision runs through the whole feature and is worth stating where it starts: this
-- exists to reassure, not to score. There is deliberately no target column, no streak counter and
-- no rating of the relationship anywhere below. The point of recording that something happened is
-- to be able to check, on a bad evening, that it happened recently -- and a schema that could
-- express "you are 2 under this week" would eventually be made to say so on screen.

-- One typed table for everything that happened, the way finance_transactions uses txn_type. A date
-- night, a note left on the counter and a gift are the same shape with a different kind, so "when
-- did we last..." is one query rather than four, and a new kind later is an enum value rather than
-- a migration and a screen.
CREATE TABLE relationship_moments (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

  -- A date, not a timestamp. These get logged the next morning, and the hour is not information
  -- worth storing about any of them.
  occurred_on   DATE NOT NULL,

  kind          VARCHAR(20) NOT NULL CHECK (kind IN (
                  'DATE_NIGHT', 'NOTE_LEFT', 'GIFT_GIVEN', 'INTIMACY',
                  'CONVERSATION', 'TRIP', 'GESTURE')),

  -- Where you went, what you talked about. Optional, and the thing that makes the timeline worth
  -- re-reading in a year rather than being a row of dates.
  note          VARCHAR(1000) NOT NULL DEFAULT '',

  -- Optional 1-3. Not a rating of her and not a rating of the event -- a note to yourself about
  -- how the week felt, which is the thing that actually drifts without anyone noticing. Never
  -- charted; only ever shown as context on the week it belongs to.
  felt_close    SMALLINT CHECK (felt_close BETWEEN 1 AND 3),

  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every read is "the latest of this kind" or "the timeline", both newest-first.
CREATE INDEX idx_rel_moments_kind_date ON relationship_moments (kind, occurred_on DESC);
CREATE INDEX idx_rel_moments_date ON relationship_moments (occurred_on DESC);

-- Ideas you had on a good day, waiting for a day when you have none.
CREATE TABLE relationship_ideas (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

  kind          VARCHAR(10) NOT NULL DEFAULT 'GIFT' CHECK (kind IN ('GIFT', 'DATE', 'GESTURE')),
  title         VARCHAR(300) NOT NULL,
  detail        VARCHAR(1000) NOT NULL DEFAULT '',

  -- Free text rather than a foreign key to an occasion: "her birthday" is useful on an idea long
  -- before you have bothered to create the occasion row, and the link is only ever advisory.
  occasion      VARCHAR(120),

  -- Both optional and both there for the same reason: on an ordinary evening the deciding factor
  -- is how much effort something takes, so the list can put the two-minute options first.
  est_cost      NUMERIC(10, 2),
  effort        VARCHAR(10) CHECK (effort IN ('SMALL', 'MEDIUM', 'BIG')),

  status        VARCHAR(10) NOT NULL DEFAULT 'IDEA' CHECK (status IN ('IDEA', 'PLANNED', 'DONE')),

  -- Set when the idea is acted on, which is what closes the loop between this table and the
  -- moments table and stops the list filling with things already done.
  done_moment_id BIGINT REFERENCES relationship_moments (id) ON DELETE SET NULL,

  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rel_ideas_status ON relationship_ideas (status, kind, id DESC);

-- Anniversaries and birthdays. The only part of the feature with an actual deadline.
CREATE TABLE relationship_occasions (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  label         VARCHAR(120) NOT NULL,

  -- The original date. For a recurring occasion only the month and day matter, but the full date
  -- is kept so "our 4th anniversary" can be worked out rather than typed in every year.
  occasion_date DATE NOT NULL,
  recurring     BOOLEAN NOT NULL DEFAULT TRUE,

  -- How far ahead to start showing it. A birthday needs more warning than a monthly dinner, and
  -- an idea that surfaces the day before is not much use.
  lead_days     INT NOT NULL DEFAULT 21 CHECK (lead_days >= 0),

  note          VARCHAR(500) NOT NULL DEFAULT '',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Articles, books and podcasts, with the field that matters most at the end.
CREATE TABLE relationship_reading (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title         VARCHAR(300) NOT NULL,
  url           VARCHAR(1000),
  source        VARCHAR(200),
  kind          VARCHAR(10) NOT NULL DEFAULT 'ARTICLE' CHECK (kind IN (
                  'ARTICLE', 'BOOK', 'PODCAST')),
  status        VARCHAR(10) NOT NULL DEFAULT 'TO_READ' CHECK (status IN ('TO_READ', 'READ')),

  -- The point of reading one of these is what you would do differently. A reading list without
  -- this column is a list of things you can say you read.
  takeaway      VARCHAR(1000) NOT NULL DEFAULT '',

  read_on       DATE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rel_reading_status ON relationship_reading (status, id DESC);

--rollback DROP TABLE relationship_reading;
--rollback DROP TABLE relationship_occasions;
--rollback DROP TABLE relationship_ideas;
--rollback DROP TABLE relationship_moments;
