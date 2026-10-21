-- Local demo records only. NEVER run on production.
-- Run AFTER 04_admin_operations_schema.sql. Safe to re-run; no live gateway/payment rows inserted.
BEGIN;
-- Pending Chef applicant for verification demo; isolated .test identity.
INSERT INTO users(username,email,password,email_verified,is_active)
SELECT 'Đầu bếp Lâm Ngọc Tú','chef.pending@gocook.test',
       crypt('Demo@123456',gen_salt('bf',10)),true,true
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='chef.pending@gocook.test');
INSERT INTO user_roles(user_id,role_id)
SELECT u.id,r.id FROM users u CROSS JOIN roles r
WHERE u.email='chef.pending@gocook.test' AND r.role_name IN ('USER','INSTRUCTOR')
AND NOT EXISTS(SELECT 1 FROM user_roles ur
  WHERE ur.user_id=u.id AND ur.role_id=r.id AND ur.is_active=true);
INSERT INTO chef_profiles(user_id,display_name,bio,phone,service_area,rating,is_verified,verification_status)
SELECT u.id,'Bếp Ngọc Tú','Ứng viên đầu bếp gia đình đang chờ xác minh hồ sơ.','0900000099',
       'TP. Hồ Chí Minh',0,false,'PENDING'
FROM users u WHERE u.email='chef.pending@gocook.test'
AND NOT EXISTS(SELECT 1 FROM chef_profiles cp WHERE cp.user_id=u.id);

INSERT INTO admin_vouchers(code,description,discount_type,discount_value,min_order_amount,max_discount,usage_limit,starts_at,ends_at,is_active)
VALUES
('GOCOOKWELCOME','Ưu đãi khách hàng mới (quy tắc checkout chờ Payment tích hợp)','PERCENT',10,500000,100000,200,now()-interval '1 day',now()+interval '60 days',false),
('HOME50K','Giảm 50.000đ (chưa kích hoạt ở checkout)','FIXED',50000,300000,NULL,100,now()-interval '1 day',now()+interval '30 days',false)
ON CONFLICT(code) DO NOTHING;
INSERT INTO admin_cases(case_code,subject,description,priority,status)
VALUES
('CASE-DEMO-001','Khách muốn thay đổi giờ phục vụ','Khách hàng cần xác nhận lại giờ nấu trước ngày đặt lịch.','MEDIUM','OPEN'),
('CASE-DEMO-002','Đầu bếp báo thay đổi địa điểm','Cần đối chiếu thông tin trước khi xử lý điều chỉnh booking.','HIGH','IN_REVIEW')
ON CONFLICT(case_code) DO NOTHING;
INSERT INTO admin_tickets(ticket_code,name,contact_email,subject,message,priority,status)
VALUES
('TICKET-DEMO-001','Nguyễn Hà','contact.demo1@gocook.test','Hỏi về thực đơn chay','Tôi muốn biết cách tìm đầu bếp chuyên món chay trên nền tảng.','LOW','OPEN'),
('TICKET-DEMO-002','Trần Nam','contact.demo2@gocook.test','Yêu cầu cập nhật địa chỉ','Tôi muốn cập nhật địa chỉ phục vụ trong đơn hàng sắp tới.','MEDIUM','OPEN')
ON CONFLICT(ticket_code) DO NOTHING;
INSERT INTO admin_review_reports(external_review_id,reason,status)
SELECT 100001,'Demo: nội dung đánh giá cần được kiểm duyệt sau khi module Reviews tích hợp','OPEN'
WHERE NOT EXISTS (SELECT 1 FROM admin_review_reports WHERE external_review_id=100001);
-- Refund queue is intentionally NOT seeded: requires genuine order/payment evidence.
-- Audit log is intentionally NOT seeded: fake actions compromise traceability.
COMMIT;
