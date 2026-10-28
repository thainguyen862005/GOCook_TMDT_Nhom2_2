-- BACKUP DB FIRST. Only run after precheck 11 returns no duplicate addresses.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS default_address VARCHAR(500);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_normalized ON users (lower(trim(email)));
COMMIT;
-- Redis OTP and session keys are ephemeral. No SQL account deletion or bulk changes are performed.
