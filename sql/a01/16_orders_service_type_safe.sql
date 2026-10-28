-- GO Cook | Admin + Chef/Finance compatibility | 2026-10-28
-- Apply in Navicat AFTER importing gocookdb.sql and BEFORE starting the updated backend.
-- This does NOT touch payments, escrow, menus or demo bookings except filling missing type.
-- Existing records are MENU by convention; verify any known custom-budget bookings manually.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';

DO $$
BEGIN
  IF to_regclass('public.orders') IS NULL THEN
    RAISE EXCEPTION 'Missing public.orders: select the correct GO Cook database before migrating';
  END IF;
END $$;

ALTER TABLE public.orders
  ADD COLUMN IF NOT EXISTS service_type VARCHAR(15) DEFAULT 'MENU';
-- A previously created nullable column is repaired safely.
UPDATE public.orders SET service_type = 'MENU' WHERE service_type IS NULL;
ALTER TABLE public.orders ALTER COLUMN service_type SET DEFAULT 'MENU';
ALTER TABLE public.orders ALTER COLUMN service_type SET NOT NULL;

-- Reject malformed historical values rather than silently rewriting them.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM public.orders WHERE service_type NOT IN ('MENU','BUDGET')) THEN
    RAISE EXCEPTION 'orders.service_type contains invalid values; inspect records before applying migration';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.orders'::regclass AND conname = 'chk_orders_service_type'
  ) THEN
    ALTER TABLE public.orders ADD CONSTRAINT chk_orders_service_type
      CHECK (service_type IN ('MENU','BUDGET'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orders_service_type ON public.orders(service_type);
COMMIT;
