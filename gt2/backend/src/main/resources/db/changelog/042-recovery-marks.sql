--liquibase formatted sql

--changeset casey:042-recovery-marks
-- Highlights and notes in the book, the way a pencil marks a page. A mark is on a paragraph
-- (seq), on the whole of it or on a run of words in it (start_off/end_off, offsets into the
-- body); colour null is a note without a highlight. Keyed by the slot, not the text row, because
-- an import replaces the text row and its paragraphs; the quote — the words marked — is how a
-- mark finds its paragraph again afterwards, with the printed page as the first place to look.
CREATE TABLE recovery_marks (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slot        VARCHAR(20) NOT NULL,
  seq         INT NOT NULL,
  page_label  VARCHAR(8),
  start_off   INT,
  end_off     INT,
  quote       TEXT NOT NULL,
  color       VARCHAR(12),
  note        TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((start_off IS NULL) = (end_off IS NULL)),
  CHECK (color IS NOT NULL OR note IS NOT NULL)
);
CREATE INDEX recovery_marks_slot_seq_idx ON recovery_marks (slot, seq);

--rollback DROP TABLE recovery_marks;
