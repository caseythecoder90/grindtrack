--liquibase formatted sql

--changeset casey:024-assistant-reports
-- What the assistant has drafted. Its own table, never the user's: an accepted draft is copied
-- into weekly_reviews by an explicit click on the week tab, so nothing a model wrote can reach
-- real data without a person deciding it should. That rule is why this table exists at all.
--
-- One row per (kind, week): regenerating replaces the draft rather than piling up attempts,
-- because the question the UI asks is "what is the draft for this week", singular.
--
-- Token counts ride with every draft. A feature that spends money per click should say what it
-- spent, and the status endpoint sums these into the month's bill.
CREATE TABLE assistant_reports (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind          VARCHAR(30) NOT NULL CHECK (kind IN ('weekly_review')),
  week_start    DATE NOT NULL,
  generated_at  TIMESTAMPTZ NOT NULL,
  model         VARCHAR(60) NOT NULL,
  input_tokens  BIGINT NOT NULL,
  output_tokens BIGINT NOT NULL,
  -- The structured draft, exactly as the model filled it. JSON in a text column rather than
  -- spread over six varchars: the shape belongs to ReviewDraft.java, and adding a field there
  -- must not need a migration here.
  draft_json    TEXT NOT NULL,
  UNIQUE (kind, week_start)
);

--rollback DROP TABLE assistant_reports;
