--liquibase formatted sql

--changeset casey:038-users-role
-- Two kinds of account. The owner is the one bootstrap made and everything in the app is
-- theirs; a partner is made from the app and reaches only the paths SecurityConfig names for
-- both. Every row so far is the owner: the app had one account until now.
ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'OWNER';
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;
ALTER TABLE users ADD CONSTRAINT users_role_known CHECK (role IN ('OWNER', 'PARTNER'));
-- One owner, held by the database rather than by whichever code creates accounts.
CREATE UNIQUE INDEX users_one_owner ON users (role) WHERE role = 'OWNER';
--rollback DROP INDEX users_one_owner; ALTER TABLE users DROP CONSTRAINT users_role_known; ALTER TABLE users DROP COLUMN role;

--changeset casey:038-push-subscriptions-user
-- A device belongs to the account that subscribed it, so a partner's phone is never told the
-- owner's todos. Every row so far is the owner's.
ALTER TABLE push_subscriptions ADD COLUMN user_id BIGINT REFERENCES users (id) ON DELETE CASCADE;
UPDATE push_subscriptions SET user_id = (SELECT id FROM users WHERE role = 'OWNER');
ALTER TABLE push_subscriptions ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
--rollback DROP INDEX idx_push_subscriptions_user; ALTER TABLE push_subscriptions DROP COLUMN user_id;
