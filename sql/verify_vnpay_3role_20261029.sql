-- GO Cook / read-only, PostgreSQL. Run with Navicat AFTER applying your existing migrations.
-- DOES NOT insert, update, delete, drop or mark any payment as successful.

-- 1. Check required payment columns/function and booking schema.
SELECT 'payment_transactions.' || c AS check_item,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='public' AND table_name='payment_transactions' AND column_name=c) AS ok
FROM (VALUES ('attempt_no'),('idempotency_key'),('expires_at'),('close_reason')) AS q(c)
UNION ALL
SELECT 'function:register_verified_payment',
       to_regprocedure('public.gocook_register_verified_payment(bigint,bigint)') IS NOT NULL
UNION ALL
SELECT 'table:order_escrows',to_regclass('public.order_escrows') IS NOT NULL
UNION ALL
SELECT 'table:chef_bookings',to_regclass('public.chef_bookings') IS NOT NULL;

-- 2. Read the newest real orders across Customer / Chef / Admin.
--   An order with order_status=PENDING, payment_status=PENDING and no escrow IS UNPAID.
SELECT o.id, o.order_code, o.customer_id, o.chef_id, o.service_type,
       o.status AS order_status, o.total_amount AS required_vnd,
       p.transaction_code AS latest_vnpay_ref, p.status AS payment_status,
       p.amount AS payment_vnd, p.attempt_no, p.expires_at,
       e.status AS escrow_status, e.payment_transaction_id AS escrow_payment_id,
       CASE WHEN e.status='HELD' THEN 'DA_THU_TIEN_CHO_CHEF_NHAN'
            WHEN e.status='RELEASED' THEN 'DA_GIAI_NGAN_NOI_BO'
            WHEN e.status IS NULL AND p.status='SUCCESS' THEN 'CAN_DOI_SOAT_ESCROW_KHONG_THU_LAN_HAI'
            WHEN o.status='CANCELLED' THEN 'DA_HUY_DON'
            ELSE 'CHUA_CO_XAC_NHAN_THANH_TOAN' END AS actual_state
FROM orders o
LEFT JOIN LATERAL (
  SELECT * FROM payment_transactions x WHERE x.order_id=o.id
  ORDER BY x.attempt_no DESC,x.id DESC LIMIT 1
) p ON true
LEFT JOIN order_escrows e ON e.order_id=o.id
WHERE o.is_active=true
ORDER BY o.id DESC LIMIT 30;

-- 3. Monetary consistency (any row warrants investigation).
SELECT o.order_code,o.total_amount AS order_amount,p.amount AS paid_attempt_amount,
       p.status,p.transaction_code
FROM payment_transactions p JOIN orders o ON o.id=p.order_id
WHERE p.amount<>o.total_amount;

-- 4. SUCCESS attempts without escrow: do not retry charge blindly.
--   If multiple SUCCESS attempts exist, excess attempts must be reviewed for refund.
SELECT o.id,o.order_code,p.id AS payment_id,p.transaction_code,p.amount,
       p.paid_at,p.status,e.id AS escrow_id,e.status AS escrow_status,
       CASE WHEN e.payment_transaction_id IS NOT NULL AND e.payment_transaction_id<>p.id
            THEN 'DUPLICATE_SUCCESS_REVIEW_REFUND'
            ELSE 'REVIEW_ESCROW_MISSING' END AS risk
FROM payment_transactions p JOIN orders o ON o.id=p.order_id
LEFT JOIN order_escrows e ON e.order_id=o.id
WHERE p.status='SUCCESS' AND (e.id IS NULL OR e.payment_transaction_id<>p.id)
ORDER BY p.id DESC;

-- 5. Wrong escrow split / wrong payer/chef assignment (should return ZERO rows).
SELECT o.order_code,e.id,e.gross_amount,e.platform_fee,e.chef_amount,
       e.chef_id AS escrow_chef_id,o.chef_id AS order_chef_id,
       p.id AS payment_id,p.amount AS gateway_amount
FROM order_escrows e JOIN orders o ON o.id=e.order_id
JOIN payment_transactions p ON p.id=e.payment_transaction_id
WHERE e.gross_amount<>e.platform_fee+e.chef_amount
  OR e.gross_amount<>o.total_amount OR p.amount<>e.gross_amount
  OR p.status<>'SUCCESS' OR e.chef_id<>o.chef_id;

-- 6. Chef-side mapping and booking status for newly created orders.
SELECT o.id,o.order_code,o.chef_id,u.username AS chef_username,
       cp.is_verified AS chef_verified,cp.is_active AS profile_active,
       o.status AS order_status,e.status AS escrow_status,
       b.id AS booking_id,b.booking_status,b.starts_at,b.ends_at
FROM orders o LEFT JOIN users u ON u.id=o.chef_id
LEFT JOIN chef_profiles cp ON cp.user_id=o.chef_id
LEFT JOIN order_escrows e ON e.order_id=o.id
LEFT JOIN chef_bookings b ON b.order_id=o.id
WHERE o.is_active=true ORDER BY o.id DESC LIMIT 30;

-- 7. Accepted quote must point at exactly one order; selected Chef must be order owner.
SELECT r.request_code,r.customer_id,r.accepted_order_id,o.order_code,
       o.chef_id,o.status AS order_status,e.status AS escrow_status
FROM public_cooking_requests r
LEFT JOIN orders o ON o.id=r.accepted_order_id
LEFT JOIN order_escrows e ON e.order_id=o.id
WHERE r.accepted_order_id IS NOT NULL
ORDER BY r.id DESC LIMIT 30;
