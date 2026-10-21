-- Read-only Navicat checks after 09_public_navigation_dynamic.sql.
SELECT current_database() AS database_name, current_schema() AS active_schema;
SELECT name, to_regclass('public.' || name) IS NOT NULL AS exists
FROM (VALUES ('users'),('chef_profiles'),('products'),('admin_vouchers'),('admin_tickets'),
             ('public_faqs'),('public_cooking_requests')) AS v(name);

-- Expect ~60 public chefs for DEMO26 (60 verified, 10 pending).
SELECT COUNT(*) AS public_chefs
FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
WHERE cp.is_active AND cp.is_verified AND cp.verification_status='APPROVED' AND u.is_active;

SELECT COUNT(*) AS published_menus
FROM products WHERE is_active AND status='PUBLISHED';

SELECT COUNT(*) AS visible_vouchers
FROM admin_vouchers
WHERE is_active AND starts_at<=now() AND ends_at>now() AND used_count<usage_limit;

SELECT COUNT(*) AS visible_faqs FROM public_faqs WHERE is_active;
SELECT COUNT(*) AS custom_requests FROM public_cooking_requests;
SELECT COUNT(*) AS web_support_tickets FROM admin_tickets WHERE ticket_code LIKE 'WEB-%';
