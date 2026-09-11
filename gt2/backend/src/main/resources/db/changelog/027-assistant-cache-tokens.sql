--liquibase formatted sql

--changeset casey:027-assistant-cache-tokens
-- Cached input tokens, stored apart from uncached ones because they are not billed at the same
-- rate: a cache write costs 1.25x the base input price and a cache read 0.1x. Folding either into
-- input_tokens would make the month's cost on the status endpoint a number that no longer matches
-- the invoice — understating it, since the API reports cached tokens in their own fields and
-- leaves them out of input_tokens entirely.
ALTER TABLE assistant_messages
  ADD COLUMN cache_write_tokens BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN cache_read_tokens  BIGINT NOT NULL DEFAULT 0;

--rollback ALTER TABLE assistant_messages DROP COLUMN cache_write_tokens, DROP COLUMN cache_read_tokens;
