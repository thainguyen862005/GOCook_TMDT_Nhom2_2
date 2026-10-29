-- Read-only: verify prerequisites and evidence coverage.
SELECT to_regclass('public.order_ingredient_receipts') AS evidence_table,
       to_regclass('public.chef_receipt_blobs') AS private_binary_storage;
SELECT column_name,data_type,column_default FROM information_schema.columns
WHERE table_schema='public' AND table_name='order_ingredient_receipts' AND column_name='evidence_type';
SELECT o.id,o.order_code,o.status,
  count(r.id) FILTER (WHERE r.evidence_type='INGREDIENT_RECEIPT') AS ingredient_receipts,
  count(r.id) FILTER (WHERE r.evidence_type='FINISHED_MEAL') AS completed_photos
FROM public.orders o
LEFT JOIN public.order_ingredient_receipts r ON r.order_id=o.id AND r.is_active=true
WHERE o.status IN ('PREPARING','COOKING','AWAITING_ACCEPTANCE')
GROUP BY o.id,o.order_code,o.status
ORDER BY o.id DESC LIMIT 50;
