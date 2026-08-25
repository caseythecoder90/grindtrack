--liquibase formatted sql

--changeset casey:010-finance
-- Personal finance tracking: real accounts, real transactions, and the house-fund goal.
-- Separate from every other table here because this is money, not effort — nothing in
-- tracking/work/plan carries a monetary amount, and nothing here carries hours.
--
-- Three decisions are baked in from the first migration because retrofitting them hurts:
--   1. txn_type — a credit-card payment is NOT an expense. The expense was the original
--      purchase. Counting both the purchase and the payment double-counts everything, which
--      is the most common way a tracker like this quietly starts lying. TRANSFER and PAYMENT
--      rows are excluded from every spend rollup.
--   2. fingerprint — statement exports overlap. Re-importing August after already importing
--      it must be a no-op, so every row carries a hash of the fields that identify it and a
--      unique index does the rest.
--   3. category_source — when a category is hand-fixed, a later re-import or rule change must
--      never silently overwrite it. MANUAL always beats RULE.

CREATE TABLE finance_accounts (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name                   VARCHAR(120) NOT NULL,
  institution            VARCHAR(30) NOT NULL CHECK (institution IN (
                           'CAPITAL_ONE', 'CHASE', 'WELLS_FARGO', 'BANK_OF_AMERICA',
                           'AIDVANTAGE', 'OTHER')),
  account_type           VARCHAR(20) NOT NULL CHECK (account_type IN (
                           'CHECKING', 'SAVINGS', 'CREDIT_CARD', 'LOAN')),
  -- Text, never a number: leading zeros are real (card 0948).
  last4                  VARCHAR(4),
  -- Last known balance. Entered by hand in phase 1, derived from imports later.
  -- Credit cards and loans hold a negative balance when money is owed, so a net-worth
  -- roll-up can just sum without special-casing sign per account type.
  current_balance        NUMERIC(12, 2) NOT NULL DEFAULT 0,
  balance_as_of          DATE,
  -- Drives the savings-goal progress bar: true for the accounts holding the house fund.
  counts_toward_savings  BOOLEAN NOT NULL DEFAULT FALSE,
  active                 BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order             INT NOT NULL DEFAULT 0,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_finance_accounts_active ON finance_accounts (active, sort_order);

CREATE TABLE finance_transactions (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id        BIGINT NOT NULL REFERENCES finance_accounts (id) ON DELETE CASCADE,

  -- Posted date is the one every institution supplies and the one rollups group by.
  -- Transaction date is when it actually happened; Capital One and Chase give both,
  -- Bank of America and Wells Fargo give only one.
  posted_date       DATE NOT NULL,
  transaction_date  DATE,

  -- Signed, always: negative means money left the account, positive means it arrived.
  -- Importers normalize to this from three different conventions — Capital One's separate
  -- Debit/Credit columns, its deposit accounts' Transaction Type flag, and the already-signed
  -- Amount that Chase, Bank of America and Wells Fargo export.
  amount            NUMERIC(12, 2) NOT NULL,

  -- Exactly as the bank wrote it. Never edited: it is the audit trail, and it is the input to
  -- merchant normalization, which will improve over time and want re-running.
  raw_description   VARCHAR(500) NOT NULL,
  -- Cleaned for grouping: 'Digital Card Purchase - WINGSTOP 1861 813 725 9464 FL' -> 'WINGSTOP'.
  merchant          VARCHAR(200),

  txn_type          VARCHAR(10) NOT NULL DEFAULT 'SPEND' CHECK (txn_type IN (
                      'SPEND', 'INCOME', 'TRANSFER', 'PAYMENT')),

  category          VARCHAR(80),
  -- The issuer's own guess, kept apart so it can seed rules without being mistaken for a
  -- decision a human made.
  issuer_category   VARCHAR(80),
  category_source   VARCHAR(15) NOT NULL DEFAULT 'UNCATEGORIZED' CHECK (category_source IN (
                      'UNCATEGORIZED', 'RULE', 'MANUAL')),

  -- Wells Fargo exports pending rows alongside posted ones. Pending amounts change and pending
  -- rows disappear, so they are flagged and held out of rollups until they post.
  pending           BOOLEAN NOT NULL DEFAULT FALSE,

  -- Bank of America supplies a genuine unique reference number; every other source gets a
  -- computed hash. Both land here.
  fingerprint       VARCHAR(64) NOT NULL,

  notes             VARCHAR(500) NOT NULL DEFAULT '',
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The dedupe guarantee. Scoped per account so two cards can legitimately share a date, amount
-- and merchant on the same day without colliding.
CREATE UNIQUE INDEX uq_finance_txn_fingerprint ON finance_transactions (account_id, fingerprint);

-- Every rollup is "this account, this date range", and the ledger view is newest first.
CREATE INDEX idx_finance_txn_account_date ON finance_transactions (account_id, posted_date DESC);
-- Spend by category over a window, with transfers excluded by the caller.
CREATE INDEX idx_finance_txn_date_type ON finance_transactions (posted_date, txn_type);
-- Powers "what do I actually spend on caffeine".
CREATE INDEX idx_finance_txn_merchant ON finance_transactions (merchant);

CREATE TABLE finance_savings_goals (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name           VARCHAR(120) NOT NULL,
  target_amount  NUMERIC(12, 2) NOT NULL,
  target_date    DATE,
  -- Free text for the reasoning behind the number, so the goal still explains itself in a year.
  note           VARCHAR(500) NOT NULL DEFAULT '',
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order     INT NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

--rollback DROP TABLE finance_savings_goals;
--rollback DROP TABLE finance_transactions;
--rollback DROP TABLE finance_accounts;
