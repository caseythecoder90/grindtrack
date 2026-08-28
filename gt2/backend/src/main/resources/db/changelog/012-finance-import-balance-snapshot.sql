--liquibase formatted sql

--changeset casey:012-finance-import-balance-snapshot
-- Undo was incomplete. A Capital One deposit or Aidvantage import overwrites the account balance
-- from the statement, but undoing that import only removed the transactions -- the balance stayed
-- where the import put it, with no record of what it had been. Undoing a file uploaded into the
-- wrong account therefore cleaned up the rows and left the wrong balance behind permanently.
--
-- Snapshotting the previous reading on the batch makes undo whole. Nullable because most imports
-- (every card format) assert no balance and so overwrite nothing.
ALTER TABLE finance_import_batches
  ADD COLUMN previous_balance        NUMERIC(12, 2),
  ADD COLUMN previous_balance_as_of  DATE,
  -- Distinguishes "this import did not touch the balance" from "it set it to NULL", which the two
  -- columns above cannot express on their own.
  ADD COLUMN balance_overwritten     BOOLEAN NOT NULL DEFAULT FALSE;

--rollback ALTER TABLE finance_import_batches DROP COLUMN balance_overwritten;
--rollback ALTER TABLE finance_import_batches DROP COLUMN previous_balance_as_of;
--rollback ALTER TABLE finance_import_batches DROP COLUMN previous_balance;
