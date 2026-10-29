-- GO Cook: fill the two profile columns already described by sql/c06_account_migration.sql.
-- Backup the database before running this migration. Re-running is safe.
-- This migration does NOT modify existing profile values or payment/order data.
BEGIN;
DO $$
BEGIN
  IF to_regclass('public.users') IS NULL THEN
    RAISE EXCEPTION 'public.users does not exist; check the current database/schema';
  END IF;
END $$;

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS dietary_preferences VARCHAR(500),
  ADD COLUMN IF NOT EXISTS allergy_info VARCHAR(500);
COMMIT;

SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'users'
  AND column_name IN ('dietary_preferences', 'allergy_info')
ORDER BY column_name;
