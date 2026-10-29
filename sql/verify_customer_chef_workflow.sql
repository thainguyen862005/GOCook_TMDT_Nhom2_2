-- READ ONLY: run on the database used by Spring Boot.
-- All workflow tables below must be present before testing.
SELECT relation_name, to_regclass('public.' || relation_name) IS NOT NULL AS exists_in_public
FROM (VALUES
  ('users'),('chef_profiles'),('products'),('orders'),('chef_bookings'),
  ('public_cooking_requests'),('cooking_request_matches'),('cooking_request_offers'),
  ('cooking_request_messages'),('order_messages'),('payment_transactions'),('order_escrows')
) q(relation_name) ORDER BY relation_name;

-- Last 30 orders: check matching customer / chef assignment and escrow together.
SELECT o.id,o.order_code,o.customer_id,o.chef_id,o.status,o.total_amount,
       cb.booking_status,es.status AS escrow_status,
       (SELECT count(*) FROM payment_transactions pt WHERE pt.order_id=o.id AND pt.status='SUCCESS') AS successful_attempts
FROM orders o
LEFT JOIN chef_bookings cb ON cb.order_id=o.id
LEFT JOIN order_escrows es ON es.order_id=o.id
ORDER BY o.id DESC LIMIT 30;

-- Last 30 custom requests, matching and offers, without modifying data.
SELECT r.id,r.request_code,r.customer_id,r.status,r.accepted_order_id,
       (SELECT count(*) FROM cooking_request_matches m WHERE m.request_id=r.id) AS matched_chefs,
       (SELECT count(*) FROM cooking_request_offers f WHERE f.request_id=r.id) AS offers
FROM public_cooking_requests r ORDER BY r.id DESC LIMIT 30;
