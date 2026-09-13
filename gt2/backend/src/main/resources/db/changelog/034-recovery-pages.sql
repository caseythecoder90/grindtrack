--liquibase formatted sql

--changeset casey:034-recovery-pages
-- The book is read by the page now, two a day, and can be opened anywhere. Each paragraph
-- carries the page it starts on: the label as printed ("xvi", "58") and a running number across
-- the whole book, which is what the assignment counts. The uploaded files are kept, so a better
-- parser later re-reads them without another upload. People in recovery and the calls to them
-- are the other half of this changeset.
ALTER TABLE recovery_paragraphs ADD COLUMN page_label VARCHAR(8);
ALTER TABLE recovery_paragraphs ADD COLUMN page_seq INT NOT NULL DEFAULT 0;
CREATE INDEX recovery_paragraphs_page_idx ON recovery_paragraphs (text_id, page_seq);

CREATE TABLE recovery_files (
  id          BIGSERIAL PRIMARY KEY,
  slot        VARCHAR(16)  NOT NULL CHECK (slot IN ('big_book', 'reflection', 'meditation')),
  ordinal     INT          NOT NULL,
  filename    VARCHAR(200) NOT NULL,
  bytes       BYTEA        NOT NULL,
  size        INT          NOT NULL,
  uploaded_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX recovery_files_slot_idx ON recovery_files (slot, ordinal);

-- Minutes gave way to pages. The last day the reading was done is what the carry-over is
-- counted from; the place is where the reader was last opened.
ALTER TABLE recovery_settings DROP COLUMN read_minutes;
ALTER TABLE recovery_settings ADD COLUMN pages_per_day INT NOT NULL DEFAULT 2;
ALTER TABLE recovery_settings ADD COLUMN read_last_done DATE;
ALTER TABLE recovery_settings ADD COLUMN reading_place INT;

-- The people: a sponsor, a sponsor still to be asked, friends in the program. Each has a
-- cadence; each call is a row. Archived rather than deleted, because the calls are a record.
CREATE TABLE recovery_people (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(80)  NOT NULL,
  role         VARCHAR(12)  NOT NULL CHECK (role IN ('sponsor', 'prospect', 'friend')),
  cadence_days INT          NOT NULL DEFAULT 7,
  note         VARCHAR(300),
  archived     BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE recovery_contacts (
  id        BIGSERIAL PRIMARY KEY,
  person_id BIGINT       NOT NULL REFERENCES recovery_people(id) ON DELETE CASCADE,
  at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  note      VARCHAR(300)
);
CREATE INDEX recovery_contacts_person_idx ON recovery_contacts (person_id, at DESC);

--rollback DROP TABLE recovery_contacts;
--rollback DROP TABLE recovery_people;
--rollback ALTER TABLE recovery_settings DROP COLUMN reading_place;
--rollback ALTER TABLE recovery_settings DROP COLUMN read_last_done;
--rollback ALTER TABLE recovery_settings DROP COLUMN pages_per_day;
--rollback ALTER TABLE recovery_settings ADD COLUMN read_minutes INT NOT NULL DEFAULT 5;
--rollback DROP TABLE recovery_files;
--rollback ALTER TABLE recovery_paragraphs DROP COLUMN page_seq;
--rollback ALTER TABLE recovery_paragraphs DROP COLUMN page_label;
