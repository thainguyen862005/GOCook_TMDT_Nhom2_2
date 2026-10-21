-- Read-only verification. Execute after 04 and 05.
SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'admin_%' ORDER BY table_name;
SELECT 'vouchers' object, count(*) total FROM admin_vouchers
UNION ALL SELECT 'cases',count(*) FROM admin_cases
UNION ALL SELECT 'tickets',count(*) FROM admin_tickets
UNION ALL SELECT 'review_reports',count(*) FROM admin_review_reports
UNION ALL SELECT 'refund_requests',count(*) FROM admin_refund_requests
UNION ALL SELECT 'audit_logs',count(*) FROM admin_audit_logs;
SELECT id,user_id,display_name,is_verified,verification_status,is_active FROM chef_profiles ORDER BY id LIMIT 20;
SELECT id,code,discount_type,discount_value,is_active FROM admin_vouchers ORDER BY id;
