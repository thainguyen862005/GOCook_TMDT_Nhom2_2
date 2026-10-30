-- Apply AFTER existing orders/escrow schema. Only one new table; never alter payment ledgers.
BEGIN;
CREATE TABLE IF NOT EXISTS public.order_reviews (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES public.orders(id),
 chef_id BIGINT NOT NULL REFERENCES public.users(id),
 customer_id BIGINT NOT NULL REFERENCES public.users(id),
 rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
 body VARCHAR(1000) NOT NULL CHECK (length(btrim(body)) BETWEEN 15 AND 1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_order_reviews_chef ON public.order_reviews(chef_id, created_at DESC, id DESC);
COMMIT;
