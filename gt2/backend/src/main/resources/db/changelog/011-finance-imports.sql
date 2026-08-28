--liquibase formatted sql

--changeset casey:011-finance-imports
-- Statement importing. One row per uploaded file, so an import can be inspected after the fact
-- and undone as a unit if a file went into the wrong account.
--
-- The uploaded file itself is never stored: it is parsed in memory and discarded. This repo is
-- public and the deployed instance holds real money data, so a statement on disk is a liability
-- with no upside — everything worth keeping is already in finance_transactions.
CREATE TABLE finance_import_batches (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id        BIGINT NOT NULL REFERENCES finance_accounts (id) ON DELETE CASCADE,

  filename          VARCHAR(255) NOT NULL,
  -- Which parser handled it, detected from the header rather than chosen by hand.
  source_format     VARCHAR(40) NOT NULL,

  -- Counts, so the result screen can explain itself: 40 rows in the file, 12 new, 27 already
  -- present, 1 pending and therefore held back.
  rows_in_file      INT NOT NULL DEFAULT 0,
  rows_imported     INT NOT NULL DEFAULT 0,
  rows_duplicate    INT NOT NULL DEFAULT 0,
  rows_pending      INT NOT NULL DEFAULT 0,
  rows_skipped      INT NOT NULL DEFAULT 0,

  -- Covered range, so gaps between imports are visible at a glance.
  period_start      DATE,
  period_end        DATE,

  imported_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_finance_imports_account ON finance_import_batches (account_id, imported_at DESC);

-- Nullable on purpose: rows entered by hand in phase 1 have no batch, and deleting a batch
-- must not cascade into deleting them.
ALTER TABLE finance_transactions
  ADD COLUMN import_batch_id BIGINT REFERENCES finance_import_batches (id) ON DELETE SET NULL;

CREATE INDEX idx_finance_txn_batch ON finance_transactions (import_batch_id);

--rollback DROP INDEX idx_finance_txn_batch;
--rollback ALTER TABLE finance_transactions DROP COLUMN import_batch_id;
--rollback DROP TABLE finance_import_batches;
