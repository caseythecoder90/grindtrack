--liquibase formatted sql

--changeset casey:037-bible-browse
-- The Bible passage becomes a cursor rather than a date: one passage a day still, but "another
-- passage" moves it on, so a morning with time for two is two. NULL bible_cursor means the row
-- has not been converted yet; the first read computes it from bible_plan_start the old way, so
-- nobody's place jumps. bible_shown_on is the day the cursor was last set, so a new day advances
-- it once.
ALTER TABLE recovery_settings ADD COLUMN bible_cursor INT;
ALTER TABLE recovery_settings ADD COLUMN bible_shown_on DATE;

-- Searching the Bible, the same way as the book: English stemming, so "shepherd" finds
-- "shepherds".
CREATE INDEX bible_verses_fts_idx ON bible_verses
  USING GIN (to_tsvector('english', text));

-- What a passage means, in plain words, written once by the model and kept: the same passage
-- read on two phones, or next time round the plan, is one call, not three.
CREATE TABLE bible_notes (
  passage_key  VARCHAR(30) PRIMARY KEY,   -- "JHN 3:16-21"
  body         TEXT NOT NULL,
  model        VARCHAR(60) NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

--rollback DROP TABLE bible_notes;
--rollback DROP INDEX bible_verses_fts_idx;
--rollback ALTER TABLE recovery_settings DROP COLUMN bible_shown_on;
--rollback ALTER TABLE recovery_settings DROP COLUMN bible_cursor;
