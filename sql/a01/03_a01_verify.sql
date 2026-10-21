-- Read-only verification in Navicat. All counts refer to the current database.
SELECT table_name FROM information_schema.tables
WHERE table_schema='public' AND table_name IN ('users','roles','user_roles','products','orders','chef_profiles')
ORDER BY table_name;

SELECT column_name,data_type FROM information_schema.columns
WHERE table_schema='public' AND table_name IN ('products','orders')
ORDER BY table_name,ordinal_position;

SELECT r.role_name, COUNT(DISTINCT ur.user_id) AS assigned_users
FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id AND ur.is_active
GROUP BY r.role_name ORDER BY r.role_name;

SELECT u.email, u.username, u.is_active, string_agg(DISTINCT r.role_name,', ' ORDER BY r.role_name) AS roles
FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id AND ur.is_active
LEFT JOIN roles r ON r.id=ur.role_id
WHERE u.email LIKE '%@gocook.test'
GROUP BY u.id,u.email,u.username,u.is_active ORDER BY u.email;

SELECT p.name,p.status,p.unit_price,p.seller_id,u.username AS chef
FROM products p LEFT JOIN users u ON u.id=p.seller_id
WHERE p.name LIKE 'A01 - %' ORDER BY p.name;

SELECT order_code,status,scheduled_at,total_amount,customer_id,chef_id
FROM orders WHERE order_code LIKE 'GC-A01-DEMO-%'
ORDER BY order_code;

-- Admin-only revenue display uses COMPLETED scheduled_at; demo has no payment transaction.
SELECT DATE(scheduled_at) AS service_date, COUNT(*) AS completed_orders,
 SUM(total_amount) AS demo_service_value
FROM orders WHERE status='COMPLETED' AND order_code LIKE 'GC-A01-DEMO-%'
GROUP BY DATE(scheduled_at) ORDER BY service_date;
