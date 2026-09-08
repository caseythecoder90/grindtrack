--liquibase formatted sql

--changeset casey:021-refresh-rotation-grace
-- Rotation reuse detection revokes every live token for the user, forcing a fresh
-- password + TOTP login on every device. That is the right answer to a stolen token
-- and the wrong answer to the two ways a legitimate client presents an already-rotated
-- one:
--
--   * Two windows sharing a cookie jar. The installed desktop app and the same site in
--     a browser tab are one jar. Both wake, both see an expired access cookie, both
--     POST /api/auth/refresh with the same token. One rotates it; the other arrives a
--     few milliseconds later holding what is now a revoked token.
--   * A rotation whose response never arrived. The server committed the new token and
--     the pod was replaced mid-deploy, or the phone suspended before the response
--     landed. The client still holds the old one and presents it next time.
--
-- Neither is theft, and both were indistinguishable from it because nothing recorded
-- WHEN a token was rotated away. This column records that instant, so a presentation
-- within the grace window can be treated as the race it almost certainly is, while a
-- replay hours later still trips the cascade.
--
-- NULL means "not rotated": either still live, or revoked by an explicit logout. A
-- logged-out token deliberately gets no grace -- presenting one is not a race.
ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;

COMMENT ON COLUMN refresh_tokens.rotated_at IS
  'When this token was exchanged for a successor. NULL if live or revoked by logout.';
