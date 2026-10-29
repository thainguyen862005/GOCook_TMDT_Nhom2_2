-- Diagnostic only. Run the first block BEFORE migrations; remaining blocks AFTER.
-- BLOCK 1: Schema checks (0 means missing and migration required)
SELECT current_database() AS active_database,
 to_regclass('public.public_cooking_requests') AS requests_table,
 to_regclass('public.cooking_request_matches') AS matching_table,
 to_regclass('public.cooking_request_offers') AS offers_table,
 to_regclass('public.cooking_request_messages') AS request_chat_table,
 to_regclass('public.order_messages') AS order_chat_table,
 to_regclass('public.app_notifications') AS notifications_table,
 (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema='public' AND table_name='public_cooking_requests'
   AND column_name='requested_area') AS region_column_count;

-- BLOCK 2: A Chef must be APPROVED, active, verified and have the correct role.
SELECT cp.user_id,cp.display_name,cp.service_area,cp.is_verified,
 cp.is_active,cp.verification_status,
 EXISTS(SELECT 1 FROM user_roles ur JOIN roles ro ON ro.id=ur.role_id
        WHERE ur.user_id=cp.user_id AND ur.is_active=true
        AND ro.role_name='INSTRUCTOR') AS has_chef_role
FROM chef_profiles cp ORDER BY cp.user_id DESC LIMIT 80;

-- BLOCK 3: Request matches and Chef inbox queue (check IDs and request dates).
SELECT r.id,r.request_code,r.status,r.requested_area,r.preferred_menu_id,
 r.preferred_chef_id,m.chef_id,cp.display_name AS matched_chef,
 o.proposed_price AS chef_offer,o.status AS offer_status,
 r.created_at
FROM public_cooking_requests r
LEFT JOIN cooking_request_matches m ON m.request_id=r.id
LEFT JOIN chef_profiles cp ON cp.user_id=m.chef_id
LEFT JOIN cooking_request_offers o ON o.request_id=r.id AND o.chef_id=m.chef_id
ORDER BY r.id DESC,m.chef_id LIMIT 70;

-- BLOCK 4: Notifications for matching, offers and messages.
SELECT id,recipient_id,type,title,action_path,created_at
FROM app_notifications
WHERE type IN ('REQUEST','BOOKING') ORDER BY id DESC LIMIT 50;

-- BLOCK 5: Request and order chat messages (use only after respective migrations).
SELECT 'REQUEST' AS thread_type,request_id AS thread_id,sender_id,recipient_id,created_at
FROM cooking_request_messages ORDER BY created_at DESC LIMIT 20;
SELECT 'ORDER' AS thread_type,order_id AS thread_id,sender_id,recipient_id,created_at
FROM order_messages ORDER BY created_at DESC LIMIT 20;
