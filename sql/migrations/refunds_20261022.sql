-- P13 refunds: one row per refund of a payment (cancel, chef reject, admin approval, duplicate payment).
-- Only creates a new table and indexes; re-runnable. Needs orders and payment_transactions.
-- admin_refund_request_id points at admin_refund_requests (admin module) without a foreign key on purpose,
-- so the admin module stays free to change its table.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

CREATE TABLE IF NOT EXISTS refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    payment_transaction_id BIGINT NOT NULL REFERENCES payment_transactions(id),
    -- Whole VND given back to the customer; retained_amount = what stays (recorded only, not paid out).
    amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    retained_amount NUMERIC(14,2) NOT NULL CHECK (retained_amount >= 0),
    policy_percent INT NOT NULL CHECK (policy_percent BETWEEN 0 AND 100),
    reason_code VARCHAR(30) NOT NULL
        CHECK (reason_code IN ('CHEF_REJECTED','CUSTOMER_CANCEL','ADMIN_APPROVED','DUPLICATE_PAYMENT')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','REFUNDED','FAILED')),
    gateway_reference VARCHAR(255),
    failure_message VARCHAR(1000),
    admin_refund_request_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A payment can be refunded once; a FAILED refund does not block a retry.
CREATE UNIQUE INDEX IF NOT EXISTS ux_refunds_one_active_per_payment ON refunds(payment_transaction_id)
  WHERE status IN ('REQUESTED','REFUNDED');
CREATE INDEX IF NOT EXISTS idx_refunds_order ON refunds(order_id);
CREATE INDEX IF NOT EXISTS idx_refunds_admin_request ON refunds(admin_refund_request_id)
  WHERE admin_refund_request_id IS NOT NULL;

COMMIT;
