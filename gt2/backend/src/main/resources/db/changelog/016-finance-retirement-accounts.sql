--liquibase formatted sql

--changeset casey:016-finance-retirement-accounts
-- A 401k is real money and belongs in net worth, but it is not the house fund and must never be
-- counted as though it were: it cannot be spent on a down payment without a penalty, and a savings
-- bar that quietly includes it would report being far closer to a house than is true. That is the
-- same class of lie as counting a credit-card payment as spending, which is why RETIREMENT is its
-- own type rather than being filed under SAVINGS.
--
-- No parser maps to RETIREMENT, so the format/account-type guard already refuses every statement
-- import into one for free. These are balance-only accounts, updated by hand.
ALTER TABLE finance_accounts DROP CONSTRAINT IF EXISTS finance_accounts_account_type_check;

ALTER TABLE finance_accounts
  ADD CONSTRAINT finance_accounts_account_type_check
  CHECK (account_type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'LOAN', 'RETIREMENT'));

--rollback ALTER TABLE finance_accounts DROP CONSTRAINT finance_accounts_account_type_check;
--rollback ALTER TABLE finance_accounts ADD CONSTRAINT finance_accounts_account_type_check CHECK (account_type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'LOAN'));
