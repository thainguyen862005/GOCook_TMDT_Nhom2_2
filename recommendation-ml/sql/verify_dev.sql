-- READ ONLY: inspect whether DEV can support offline evaluation.
SELECT COUNT(*) AS published_menus FROM public.products
WHERE is_active=TRUE AND status='PUBLISHED' AND seller_id IS NOT NULL;
SELECT COUNT(DISTINCT o.customer_id) AS customers_with_verified_menu_purchases,
       COUNT(*) AS verified_menu_item_events
FROM public.orders o
JOIN public.order_escrows e ON e.order_id=o.id
JOIN public.order_items oi ON oi.order_id=o.id
WHERE o.service_type='MENU' AND o.status='COMPLETED' AND e.status='RELEASED'
  AND oi.product_id IS NOT NULL;
