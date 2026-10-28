-- P10 payment attempts: one order can have several payment attempts (retry after cancel or timeout).
-- Only ADDs columns/indexes and backfills; re-runnable. Run after payment_idempotency_20261021.sql.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

-- Attempt 1 keeps vnp_TxnRef = order code; attempt n > 1 uses order code + 'A' + n.
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS attempt_no INT NOT NULL DEFAULT 1;
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
-- USER_CANCELLED | GATEWAY_FAILED | EXPIRED; set only when an attempt is closed without SUCCESS.
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS close_reason VARCHAR(30);

-- Existing rows only know their expiry inside raw_reference ("expireDate":"yyyyMMddHHmmss", Asia/Ho_Chi_Minh wall time).
UPDATE payment_transactions
   SET expires_at = (to_timestamp(substring(raw_reference FROM '"expireDate":"([0-9]{14})"'), 'YYYYMMDDHH24MISS')
                       AT TIME ZONE current_setting('TimeZone')) AT TIME ZONE 'Asia/Ho_Chi_Minh'
 WHERE expires_at IS NULL
   AND raw_reference ~ '"expireDate":"[0-9]{14}"';

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_order_attempt ON payment_transactions(order_id, attempt_no);

COMMIT;
