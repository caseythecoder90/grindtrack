--liquibase formatted sql

--changeset casey:035-recovery-search
-- Searching the book: a full-text index over the paragraphs, so "half measures" finds the page in
-- a meeting without the physical book. English stemming, so "surrender" finds "surrendered".
CREATE INDEX recovery_paragraphs_fts_idx ON recovery_paragraphs
  USING GIN (to_tsvector('english', body));

--rollback DROP INDEX recovery_paragraphs_fts_idx;
