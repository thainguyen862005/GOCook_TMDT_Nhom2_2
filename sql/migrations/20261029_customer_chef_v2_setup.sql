-- Run once on active database, after 20261029_main_workflow.sql.
-- Safe to rerun, no DROP, no DELETE, no updates to payments/orders/escrows.
BEGIN;
ALTER TABLE public.public_cooking_requests
  ADD COLUMN IF NOT EXISTS requested_area VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_public_cooking_requests_area
  ON public.public_cooking_requests(requested_area);
CREATE TABLE IF NOT EXISTS public.order_messages (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
  sender_id BIGINT NOT NULL REFERENCES public.users(id),
  recipient_id BIGINT NOT NULL REFERENCES public.users(id),
  body VARCHAR(2000) NOT NULL CHECK (length(btrim(body)) > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_order_messages_not_self CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS idx_order_messages_thread ON public.order_messages(order_id,id DESC);
COMMIT;
