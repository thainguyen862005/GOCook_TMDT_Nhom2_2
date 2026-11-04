-- GO Cook / 2026-11-04. Additive; no payment, booking or user tables modified.
-- Run AFTER existing users, products, orders and order_items migrations; take a DB backup first.
BEGIN;
SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='120s';

CREATE TABLE IF NOT EXISTS public.carts (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE REFERENCES public.users(id),
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now())*1000)::bigint,
 updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now())*1000)::bigint
);
CREATE TABLE IF NOT EXISTS public.cart_items (
 id BIGSERIAL PRIMARY KEY,
 cart_id BIGINT NOT NULL REFERENCES public.carts(id) ON DELETE CASCADE,
 product_id BIGINT NOT NULL REFERENCES public.products(id),
 guest_count INT NOT NULL CHECK (guest_count>0),
 quantity INT NOT NULL DEFAULT 1 CHECK (quantity>0),
 note TEXT,
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now())*1000)::bigint,
 updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now())*1000)::bigint,
 UNIQUE(cart_id,product_id)
);
CREATE INDEX IF NOT EXISTS idx_cart_items_cart ON public.cart_items(cart_id,is_active);

CREATE TABLE IF NOT EXISTS public.menu_questions (
 id BIGSERIAL PRIMARY KEY,
 product_id BIGINT NOT NULL REFERENCES public.products(id),
 customer_id BIGINT NOT NULL REFERENCES public.users(id),
 chef_id BIGINT NOT NULL REFERENCES public.users(id),
 question VARCHAR(1500) NOT NULL CHECK (char_length(btrim(question)) BETWEEN 10 AND 1500),
 answer VARCHAR(2000),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ANSWERED','HIDDEN')),
 answered_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_menu_questions_chef ON public.menu_questions(chef_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_menu_questions_product ON public.menu_questions(product_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_menu_questions_customer ON public.menu_questions(customer_id,created_at DESC);

CREATE TABLE IF NOT EXISTS public.content_reports (
 id BIGSERIAL PRIMARY KEY,
 reporter_id BIGINT NOT NULL REFERENCES public.users(id),
 target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CHEF','CUSTOMER','MENU','QUESTION')),
 target_id BIGINT NOT NULL,
 order_id BIGINT REFERENCES public.orders(id),
 reason VARCHAR(1500) NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 15 AND 1500),
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_REVIEW','RESOLVED','DISMISSED')),
 resolution VARCHAR(2000),
 resolved_by BIGINT REFERENCES public.users(id),
 resolved_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_content_reports_status ON public.content_reports(status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_content_reports_reporter ON public.content_reports(reporter_id,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_content_reports_pending ON public.content_reports(reporter_id,target_type,target_id)
 WHERE status IN ('OPEN','IN_REVIEW');
CREATE TABLE IF NOT EXISTS public.menu_reviews (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES public.orders(id),
 product_id BIGINT NOT NULL REFERENCES public.products(id),
 customer_id BIGINT NOT NULL REFERENCES public.users(id),
 rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
 body VARCHAR(1000) NOT NULL CHECK (char_length(btrim(body)) BETWEEN 15 AND 1000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_menu_reviews_product ON public.menu_reviews(product_id,created_at DESC);
COMMIT;
