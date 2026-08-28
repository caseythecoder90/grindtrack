--liquibase formatted sql

--changeset casey:013-finance-category-rules
-- Automatic categorization. Until now every imported row landed UNCATEGORIZED and the only way to
-- give it a category was by hand, one row at a time -- which does not survive contact with 800
-- transactions.
--
-- A rule is a pattern plus the category to apply. Rules run in priority order and the first match
-- wins, so a specific rule ("WHOLEFDS" -> Groceries) can be placed ahead of a general one
-- ("AMAZON" -> Shopping) without either having to know about the other.
--
-- No rules are seeded here on purpose. Patterns are merchant names off real statements, and this
-- repo is public -- the same reason statements/ is gitignored. Rules are created from the review
-- inbox, where correcting one transaction offers to turn that correction into a rule.
CREATE TABLE finance_category_rules (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

  -- Matched against the normalized merchant first, then the raw description, so a rule written
  -- against the tidy name still catches rows imported before normalization improved.
  pattern       VARCHAR(200) NOT NULL,
  match_type    VARCHAR(12) NOT NULL DEFAULT 'CONTAINS' CHECK (match_type IN (
                  'CONTAINS', 'EQUALS', 'REGEX')),

  category      VARCHAR(80) NOT NULL,

  -- Lower runs first. Ties break on id, so rule order is always total and reproducible.
  priority      INT NOT NULL DEFAULT 100,
  active        BOOLEAN NOT NULL DEFAULT TRUE,

  -- Set on every apply pass, so a rule that has stopped matching anything is visible as dead
  -- weight rather than quietly accumulating.
  hit_count     INT NOT NULL DEFAULT 0,
  last_applied  TIMESTAMPTZ,

  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_finance_rules_order ON finance_category_rules (active, priority, id);

-- Two rules with the same pattern and match type would mean the lower-priority one is unreachable
-- dead configuration. Cheaper to reject it than to explain it later.
CREATE UNIQUE INDEX uq_finance_rules_pattern
  ON finance_category_rules (lower(pattern), match_type);

--rollback DROP TABLE finance_category_rules;
