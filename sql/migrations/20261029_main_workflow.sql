-- GO Cook main service workflow. PostgreSQL; run AFTER sql/a01/09_public_navigation_dynamic.sql,
-- sql/migrations/chef_migration_20261014.sql and sql/a01/16_orders_service_type_safe.sql.
-- Existing orders/proposals/payments are preserved. Safe to rerun.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE public_cooking_requests ADD COLUMN IF NOT EXISTS preferred_chef_id BIGINT REFERENCES users(id);
ALTER TABLE public_cooking_requests ADD COLUMN IF NOT EXISTS preferred_menu_id BIGINT REFERENCES products(id);
ALTER TABLE public_cooking_requests ADD COLUMN IF NOT EXISTS accepted_order_id BIGINT REFERENCES orders(id);

CREATE TABLE IF NOT EXISTS cooking_request_matches (
  request_id BIGINT NOT NULL REFERENCES public_cooking_requests(id) ON DELETE CASCADE,
  chef_id BIGINT NOT NULL REFERENCES users(id),
  match_score INT NOT NULL DEFAULT 0,
  match_reason VARCHAR(200) NOT NULL DEFAULT 'Gợi ý dựa trên hồ sơ đầu bếp',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(request_id, chef_id)
);
CREATE INDEX IF NOT EXISTS idx_cooking_request_matches_chef ON cooking_request_matches(chef_id);

CREATE TABLE IF NOT EXISTS cooking_request_offers (
  id BIGSERIAL PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES public_cooking_requests(id) ON DELETE CASCADE,
  chef_id BIGINT NOT NULL REFERENCES users(id),
  proposed_price NUMERIC(14,2) NOT NULL CHECK (proposed_price > 0),
  dishes VARCHAR(3000) NOT NULL,
  note VARCHAR(2000),
  status VARCHAR(20) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT','ACCEPTED','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(request_id, chef_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cooking_request_one_accepted
  ON cooking_request_offers(request_id) WHERE status='ACCEPTED';

CREATE TABLE IF NOT EXISTS cooking_request_messages (
  id BIGSERIAL PRIMARY KEY,
  request_id BIGINT NOT NULL REFERENCES public_cooking_requests(id) ON DELETE CASCADE,
  sender_id BIGINT NOT NULL REFERENCES users(id),
  recipient_id BIGINT NOT NULL REFERENCES users(id),
  body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cooking_request_messages_thread ON cooking_request_messages(request_id,created_at,id);
COMMIT;
