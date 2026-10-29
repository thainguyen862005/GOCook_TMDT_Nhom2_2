-- Run on the active GO Cook PostgreSQL database before opening order chat.
-- Additive: does not change existing orders, payments, escrow or request messages.
BEGIN;
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
