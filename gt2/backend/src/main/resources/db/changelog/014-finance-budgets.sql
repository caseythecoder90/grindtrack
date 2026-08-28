--liquibase formatted sql

--changeset casey:014-finance-budgets
-- Budgets, in two pieces, because a monthly budget has two genuinely different kinds of number in
-- it and conflating them is what makes most budget tools annoying.
--
--   1. The recurring plan. Rent is 2,725 every month and groceries are 600 every month. This is
--      set once and edited rarely.
--   2. The things that only happen in one month. A vacation, a car repair, a wedding gift. These
--      are real money that must reduce what is left this month, but they must NOT become part of
--      the recurring plan -- otherwise next month starts out believing it owes 800 for a holiday
--      that already happened.
--
-- Keeping them apart means "what do I normally spend" and "what is going on this month" stay
-- separately answerable, and editing one never corrupts the other.

CREATE TABLE finance_budgets (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

  -- Free text, matching finance_transactions.category. Not a foreign key to a category table
  -- because there isn't one: categories are whatever the rules produce.
  category        VARCHAR(80) NOT NULL,

  -- Always positive. Spending is stored negative on transactions, but a budget is a limit rather
  -- than a movement, and "-600 for groceries" reads as nonsense on a form.
  monthly_amount  NUMERIC(12, 2) NOT NULL CHECK (monthly_amount >= 0),

  note            VARCHAR(500) NOT NULL DEFAULT '',
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order      INT NOT NULL DEFAULT 0,

  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One line per category. Two budgets for Groceries is not a thing anyone means to have.
CREATE UNIQUE INDEX uq_finance_budgets_category ON finance_budgets (lower(category));
CREATE INDEX idx_finance_budgets_order ON finance_budgets (active, sort_order, id);

CREATE TABLE finance_budget_extras (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

  -- Always the first of the month. Storing a DATE rather than a year/month pair keeps range
  -- queries and ordering trivial, and the constraint keeps it honest.
  month       DATE NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),

  label       VARCHAR(120) NOT NULL,

  -- Signed: negative is an extra cost this month, positive is one-off money in (a bonus, a
  -- refund, a reimbursement from someone splitting the vacation). Same convention as
  -- finance_transactions, so nothing has to remember which way round this table is.
  amount      NUMERIC(12, 2) NOT NULL CHECK (amount <> 0),

  -- Optional. When set, the extra counts against that category's line for the month, so a $400
  -- flight shows up inside Travel rather than floating outside every category. When null it is a
  -- standalone item that only affects the month's total.
  category    VARCHAR(80),

  note        VARCHAR(500) NOT NULL DEFAULT '',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_finance_extras_month ON finance_budget_extras (month, id);

-- Expected income, and anything else that is a single global figure rather than a per-category
-- one. One row, enforced by the primary key check -- a table with exactly one row is easier to
-- reason about than a key/value store when there are two settings in it.
CREATE TABLE finance_budget_settings (
  id                       SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),

  -- Null means "work it out from what actually arrived": a trailing average of real INCOME rows
  -- beats a number typed in once and never revisited. Set it to override, which is what you want
  -- with a bonus month or a raise that has not landed in the data yet.
  expected_monthly_income  NUMERIC(12, 2),

  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO finance_budget_settings (id, expected_monthly_income) VALUES (1, NULL);

--rollback DROP TABLE finance_budget_settings;
--rollback DROP TABLE finance_budget_extras;
--rollback DROP TABLE finance_budgets;
