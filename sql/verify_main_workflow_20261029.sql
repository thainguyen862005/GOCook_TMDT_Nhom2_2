-- GO Cook Main Workflow · chỉ đọc (không UPDATE/DELETE/INSERT).
-- Chạy trong đúng database PostgreSQL mà backend sử dụng.

-- 1) Đảm bảo các bảng bắt buộc có mặt
SELECT needed.table_name, to_regclass('public.' || needed.table_name) IS NOT NULL AS present
FROM (VALUES ('orders'),('chef_bookings'),('order_escrows'),('payment_transactions'),
             ('public_cooking_requests'),('cooking_request_matches'),
             ('cooking_request_offers'),('cooking_request_messages'),('app_notifications')) AS needed(table_name)
ORDER BY needed.table_name;

-- 2) Các ràng buộc ngăn Chef bị giữ trùng giờ
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE conrelid = 'public.chef_bookings'::regclass
  AND conname IN ('ex_chef_bookings_no_overlap', 'chef_bookings_order_id_key');

-- 3) Đồng bộ Customer / Chef / Admin: cùng một order_id, đối chiếu tiền và lịch
-- Giao dịch SUCCESS không có nghĩa escrow đã HELD nếu xảy ra lỗi nghiệp vụ,
-- không tự ý cập nhật trạng thái cho khớp.
SELECT o.id AS order_id, o.order_code, o.customer_id, o.chef_id,
       o.service_type, o.status AS order_status, o.total_amount AS order_amount,
       b.id AS booking_id, b.booking_status, b.starts_at, b.ends_at,
       e.status AS escrow_status,
       p.status AS last_payment_status, p.amount AS last_payment_amount,
       p.transaction_code AS last_txn_ref
FROM public.orders o
LEFT JOIN public.chef_bookings b ON b.order_id = o.id
LEFT JOIN public.order_escrows e ON e.order_id = o.id
LEFT JOIN LATERAL (
   SELECT status, amount, transaction_code FROM public.payment_transactions pt
   WHERE pt.order_id = o.id ORDER BY pt.id DESC LIMIT 1
) p ON true
ORDER BY o.id DESC LIMIT 50;

-- 4) Đơn thực đơn có sẵn vẫn còn hiệu lực nhưng thiếu lịch (dữ liệu cũ cần rà soát thủ công)
SELECT o.id, o.order_code, o.chef_id, o.scheduled_at, o.status
FROM public.orders o
LEFT JOIN public.chef_bookings b ON b.order_id = o.id
WHERE o.service_type = 'MENU' AND o.status <> 'CANCELLED'
  AND o.chef_id IS NOT NULL AND b.id IS NULL
ORDER BY o.id DESC LIMIT 100;

-- 5) Yêu cầu -> matching -> báo giá (chỉ thống kê, không lộ nội dung chat)
SELECT r.id, r.request_code, r.status, r.accepted_order_id,
       COUNT(DISTINCT m.chef_id) AS matched_chefs,
       COUNT(DISTINCT f.id) AS total_offers,
       COUNT(DISTINCT msg.id) AS messages
FROM public.public_cooking_requests r
LEFT JOIN public.cooking_request_matches m ON m.request_id = r.id
LEFT JOIN public.cooking_request_offers f ON f.request_id = r.id
LEFT JOIN public.cooking_request_messages msg ON msg.request_id = r.id
GROUP BY r.id, r.request_code, r.status, r.accepted_order_id
ORDER BY r.id DESC LIMIT 30;

-- 6) Kiểm tra độ trễ thông báo bằng thời gian tạo (không đọc nội dung cá nhân)
SELECT type, count(*) AS total, max(created_at) AS latest_created
FROM public.app_notifications
GROUP BY type ORDER BY type;
