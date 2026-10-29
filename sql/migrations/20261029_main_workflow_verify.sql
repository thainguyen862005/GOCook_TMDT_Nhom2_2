-- Read-only verification. Run after 20261029_main_workflow.sql in Navicat.
SELECT table_name FROM information_schema.tables WHERE table_schema='public'
  AND table_name IN ('public_cooking_requests','cooking_request_matches',
                     'cooking_request_offers','cooking_request_messages','orders',
                     'order_items','chef_bookings') ORDER BY table_name;

SELECT r.request_code,r.status,COALESCE(m.count,0) AS matched_chefs,
       COALESCE(o.count,0) AS total_offers,r.accepted_order_id
FROM public_cooking_requests r
LEFT JOIN LATERAL (SELECT count(*) AS count FROM cooking_request_matches m WHERE m.request_id=r.id) m ON true
LEFT JOIN LATERAL (SELECT count(*) AS count FROM cooking_request_offers o WHERE o.request_id=r.id) o ON true
ORDER BY r.created_at DESC LIMIT 20;

SELECT cp.user_id,cp.display_name,cp.is_verified,cp.is_active,cp.verification_status
FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
WHERE u.is_active=true ORDER BY cp.user_id LIMIT 20;

-- Review legacy CLOSED intake rows that did not originate from the new accept workflow.
SELECT count(*) AS closed_without_order_review FROM public_cooking_requests
WHERE status='CLOSED' AND accepted_order_id IS NULL;
