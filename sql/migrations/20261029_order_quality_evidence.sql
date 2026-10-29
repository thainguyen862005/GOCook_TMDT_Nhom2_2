-- GO Cook | One-column, backward-compatible order evidence migration.
-- Prerequisite: chef_migration_20261014.sql and a01/20_chef_orders_and_private_receipts.sql.
-- Run after database backup, on the same database used by the Spring Boot backend.
-- Existing receipt rows remain INGREDIENT_RECEIPT; no payments, escrow or bookings are changed.
BEGIN;
SET LOCAL lock_timeout = '10s';
ALTER TABLE public.order_ingredient_receipts
  ADD COLUMN IF NOT EXISTS evidence_type VARCHAR(24) NOT NULL DEFAULT 'INGREDIENT_RECEIPT';
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM public.order_ingredient_receipts
             WHERE evidence_type NOT IN ('INGREDIENT_RECEIPT','FINISHED_MEAL')) THEN
    RAISE EXCEPTION 'Unknown evidence types: inspect data before applying constraint';
  END IF;
END $$;
ALTER TABLE public.order_ingredient_receipts
  DROP CONSTRAINT IF EXISTS ck_order_quality_evidence_type;
ALTER TABLE public.order_ingredient_receipts
  ADD CONSTRAINT ck_order_quality_evidence_type
  CHECK (evidence_type IN ('INGREDIENT_RECEIPT','FINISHED_MEAL'));
COMMIT;
