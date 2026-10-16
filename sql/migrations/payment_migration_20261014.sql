BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(30);

COMMIT;
