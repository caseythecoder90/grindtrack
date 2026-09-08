--liquibase formatted sql

--changeset casey:022-trusted-devices
-- "Trust this device": the second factor, remembered per browser.
--
-- The password is still required every time. What a trusted device buys is skipping the
-- authenticator code, which is what makes signing in on the phone tedious enough to avoid.
-- That is the same trade GitHub and Google make: the second factor proves you hold the
-- device, so once a device has proved it, proving it again every fortnight is ceremony.
--
-- Same storage rules as refresh_tokens, for the same reason: only a SHA-256 hash is kept,
-- so a database leak hands out nothing usable. Bound to a user, revocable, and expiring --
-- a token that could not be revoked would be a permanent hole in the second factor.
--
-- Deliberately NOT cleared by logout. Logging out and back in without reaching for the
-- phone is most of the point; a device is forgotten when you say so, or when it expires.
CREATE TABLE trusted_devices (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash   VARCHAR(64) NOT NULL UNIQUE,

  -- Sliding, like the session: a device you keep signing in on keeps its trust, one you
  -- stopped using loses it. A fixed expiry with no renewal would re-prompt on a working
  -- laptop every thirty days for no security gain.
  expires_at   TIMESTAMPTZ NOT NULL,
  last_used_at TIMESTAMPTZ,
  revoked      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trusted_devices_user ON trusted_devices (user_id);

--rollback DROP TABLE trusted_devices;
