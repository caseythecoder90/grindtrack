--liquibase formatted sql

--changeset casey:031-push-subscriptions
-- One browser on one device that asked to be told things. The endpoint is the push service's
-- address for it and the row's identity; the two keys are the browser's, and every payload is
-- encrypted to them. One account, so no owner column.
CREATE TABLE push_subscriptions (
  id            BIGSERIAL PRIMARY KEY,
  endpoint      TEXT NOT NULL UNIQUE,
  p256dh        TEXT NOT NULL,
  auth          TEXT NOT NULL,
  user_agent    TEXT,
  created_at    TIMESTAMPTZ NOT NULL,
  last_sent_at  TIMESTAMPTZ
);

--rollback DROP TABLE push_subscriptions;
