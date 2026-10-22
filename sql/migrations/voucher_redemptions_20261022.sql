-- P09 voucher redemptions: one row per order that used a voucher (the discount snapshot).
-- admin_vouchers (sql/a01/04_admin_operations_schema.sql) is NOT altered: used_count is the live counter.
-- Re-runnable. Needs admin_vouchers and orders to exist.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

CREATE TABLE IF NOT EXISTS voucher_redemptions (
    id BIGSERIAL PRIMARY KEY,
    voucher_id BIGINT NOT NULL REFERENCES admin_vouchers(id),
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    customer_id BIGINT NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL CHECK (discount_amount >= 0),
    -- RESERVED at checkout, CONSUMED when the payment is settled, RELEASED when the order dies unpaid.
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED','CONSUMED','RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_voucher_redemptions_usage ON voucher_redemptions(voucher_id, customer_id, status);

COMMIT;
