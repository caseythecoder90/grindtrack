--liquibase formatted sql

--changeset casey:023-refresh-token-families
-- Reuse detection revoked every live token for the USER. That is the shape of the
-- cascade in the original single-device design, and it is what has been signing every
-- device out since the app started living on more than one:
--
--   1. Device A's session ends (a logout, a cascade, a rotation whose response was lost
--      and aged past the grace). Its cookie is not cleared -- a 401 from /refresh left it
--      in the jar -- so the browser keeps presenting the dead token.
--   2. Device B signs in fresh and is working happily.
--   3. Device A opens the app. Its dead token trips the cascade, which revokes every
--      token for the user, including B's. B is signed out the moment its access cookie
--      expires: fifteen to thirty minutes later, with no idea why.
--   4. B signs in again, and is now the device holding a dead token. Repeat forever.
--
-- The fix is the one the OAuth 2.0 Security BCP (RFC 9700 s4.14.2) actually describes:
-- tokens belong to FAMILIES. A login starts a family; each rotation issues the successor
-- into the same one. Reuse of a rotated token revokes that family -- the one login it
-- descends from -- and nothing else. A stale cookie on one device can only ever end the
-- session it belonged to, which is already over. Other devices' logins are other
-- families and are never touched.
--
-- Existing rows each get a family of their own. They are all either live single-token
-- sessions (a family of one, exactly right) or dead, and a dead row's family is irrelevant.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = gen_random_uuid();
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

COMMENT ON COLUMN refresh_tokens.family_id IS
  'The login this token descends from. Reuse detection revokes a family, never a user.';
--rollback DROP INDEX idx_refresh_tokens_family;
--rollback ALTER TABLE refresh_tokens DROP COLUMN family_id;
