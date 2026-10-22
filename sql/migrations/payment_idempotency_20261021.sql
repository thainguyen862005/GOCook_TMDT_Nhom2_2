-- Checkout idempotency: one client-generated key per checkout intent.
-- Only ADDs a nullable column and a partial unique index; re-runnable.
-- Run after sql/migrations/chef_migration_20261014.sql (creates payment_transactions).
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS idempotency_key UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_idempotency_key ON payment_transactions(idempotency_key)
  WHERE idempotency_key IS NOT NULL;

COMMIT;
