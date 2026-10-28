-- Run after 12_auth_hardening_navicat.sql. Read-only.
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'users'
  AND column_name IN ('email_verified','phone','default_address')
ORDER BY column_name;
SELECT indexname, indexdef FROM pg_indexes
WHERE tablename = 'users' AND indexname = 'ux_users_email_normalized';
SELECT COUNT(*) AS pending_email_verification FROM users WHERE email_verified = FALSE;
SELECT COUNT(*) AS active_unverified_users FROM users
WHERE is_active = TRUE AND email_verified = FALSE;
