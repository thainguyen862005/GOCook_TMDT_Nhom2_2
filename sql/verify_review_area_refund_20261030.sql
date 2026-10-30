-- READ ONLY. Run in Navicat after applying migrations.
SELECT to_regclass('public.order_reviews') AS review_table,
       to_regclass('public.refunds') AS refund_table,
       to_regclass('public.order_escrows') AS escrow_table;
-- Chef area remains in old free text until selected again; these chefs will not match.
SELECT cp.user_id, cp.display_name, cp.service_area FROM public.chef_profiles cp
WHERE cp.service_area IS NULL OR cp.service_area !~ '^[0-9]{2}(,[0-9]{2})*$';
-- Possible duplicate VNPay successes requiring a queued refund case.
SELECT p.id AS payment_id, p.order_id, p.amount, p.status
FROM public.payment_transactions p JOIN public.order_escrows e ON e.order_id=p.order_id
WHERE p.status='SUCCESS' AND p.id<>e.payment_transaction_id;
-- Paid but no escrow: NEVER automatically mark refunded without reconciling.
SELECT p.id AS payment_id,p.order_id,p.amount FROM public.payment_transactions p
LEFT JOIN public.order_escrows e ON e.order_id=p.order_id
WHERE p.status='SUCCESS' AND e.order_id IS NULL;
-- Pending refunds need real provider execution/reconciliation.
SELECT r.id,r.order_id,r.amount,r.status,r.reason_code,r.created_at
FROM public.refunds r WHERE r.status IN ('REQUESTED','FAILED') ORDER BY r.id DESC LIMIT 100;
