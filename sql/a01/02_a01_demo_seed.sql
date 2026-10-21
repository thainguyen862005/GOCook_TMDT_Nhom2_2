-- GO Cook A01 demo fixtures for LOCAL DEVELOPMENT ONLY. Not real customers / not real payments.
-- Run after 01_a01_schema_safe.sql, in database app_db.
-- Accounts use PostgreSQL pgcrypto BCrypt: change ALL demo passwords before any public deployment.
-- PostgreSQL 16 standard contrib extension; if CREATE EXTENSION lacks privilege, use DB owner.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Demo accounts; no existing user account or password is changed by this script.
INSERT INTO users(username,email,password,email_verified,is_active)
SELECT v.username, v.email, crypt('Demo@123456', gen_salt('bf', 10)), TRUE, TRUE
FROM (VALUES
 ('Admin GO Cook Demo', 'admin.demo@gocook.test'),
 ('Đầu bếp Nguyễn Thị Mai', 'chef.mai@gocook.test'),
 ('Đầu bếp Trần Quốc Bình', 'chef.binh@gocook.test'),
 ('Đầu bếp Lê An Nhiên', 'chef.annhien@gocook.test'),
 ('Khách Nguyễn Ngọc Anh', 'customer.ngocanh@gocook.test'),
 ('Khách Phạm Minh Thư', 'customer.minhthu@gocook.test'),
 ('Khách Trần Thanh Hà', 'customer.thanhha@gocook.test')
) AS v(username,email)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

-- user_roles uses extra metadata columns with defaults. A user can be both USER and INSTRUCTOR.
INSERT INTO user_roles(user_id,role_id)
SELECT u.id, r.id
FROM users u
JOIN (VALUES
 ('admin.demo@gocook.test','ADMIN'),
 ('admin.demo@gocook.test','USER'),
 ('chef.mai@gocook.test','USER'),('chef.mai@gocook.test','INSTRUCTOR'),
 ('chef.binh@gocook.test','USER'),('chef.binh@gocook.test','INSTRUCTOR'),
 ('chef.annhien@gocook.test','USER'),('chef.annhien@gocook.test','INSTRUCTOR'),
 ('customer.ngocanh@gocook.test','USER'),
 ('customer.minhthu@gocook.test','USER'),
 ('customer.thanhha@gocook.test','USER')
) AS v(email, role_name) ON u.email=v.email
JOIN roles r ON r.role_name=v.role_name
WHERE NOT EXISTS (
 SELECT 1 FROM user_roles ur WHERE ur.user_id=u.id AND ur.role_id=r.id AND ur.is_active
);

INSERT INTO chef_profiles(user_id, display_name, bio, phone, service_area, rating, is_verified)
SELECT u.id, v.name, v.bio, v.phone, v.area, v.rating, TRUE
FROM (VALUES
 ('chef.mai@gocook.test','Bếp Cô Mai','Chuyên mâm cơm gia đình miền Nam, hơn 8 năm kinh nghiệm.','0900000001','TP. Hồ Chí Minh',4.90::numeric),
 ('chef.binh@gocook.test','Bếp Quốc Bình','Chuyên món Bắc và tiệc gia đình tại nhà.','0900000002','TP. Hồ Chí Minh',4.80::numeric),
 ('chef.annhien@gocook.test','Bếp An Nhiên','Bếp chay hiện đại với nhiều lựa chọn healthy.','0900000003','TP. Hồ Chí Minh',4.95::numeric)
) AS v(email,name,bio,phone,area,rating)
JOIN users u ON u.email=v.email
WHERE NOT EXISTS (SELECT 1 FROM chef_profiles cp WHERE cp.user_id=u.id);

-- Published products are owned by a Chef and use local image assets present in frontend/public/assets.
INSERT INTO products(name,category,unit_price,min_guests,max_guests,dish_items,description,image_url,status,seller_id)
SELECT v.name,v.category,v.price,v.min_guest,v.max_guest,v.dishes,v.description,v.image,'PUBLISHED',u.id
FROM (VALUES
 ('A01 - Mâm cơm Sum Vầy Miền Nam','Món miền Nam',120000::numeric,3,8,E'Cá lóc kho tộ\nCanh chua cá\nRau luộc kho quẹt\nTrứng chiên','Mâm cơm ấm cúng dành cho gia đình, phục vụ tại nhà.','/assets/meal.png','chef.mai@gocook.test'),
 ('A01 - Tiệc Gia Đình Món Bắc','Món miền Bắc',180000::numeric,4,12,E'Nem rán\nGà hấp lá chanh\nCanh mọc\nNộm đu đủ','Gói tiệc gia đình theo phong cách truyền thống miền Bắc.','/assets/family.png','chef.binh@gocook.test'),
 ('A01 - Mâm chay Thanh Lành','Món chay',105000::numeric,2,8,E'Đậu hũ sốt nấm\nCanh củ sen\nRau củ hấp\nCơm gạo lứt','Thực đơn chay phù hợp bữa cơm thanh đạm.','/assets/vegetarian.png','chef.annhien@gocook.test'),
 ('A01 - Bữa cơm Văn phòng Healthy','Món chay',135000::numeric,2,10,E'Salad mùa vụ\nĐậu hũ áp chảo\nCanh rau củ','Thực đơn ít dầu phù hợp nhóm bạn và dân văn phòng.','/assets/vegetarian.png','chef.annhien@gocook.test')
) AS v(name,category,price,min_guest,max_guest,dishes,description,image,email)
JOIN users u ON u.email=v.email
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name=v.name);

-- A draft product showcases the Admin create/edit/visibility rules.
INSERT INTO products(name,category,unit_price,min_guests,max_guests,dish_items,description,image_url,status,seller_id)
SELECT 'A01 - Thực đơn mùa lễ (bản nháp)','Món miền Nam',225000,4,12,
 E'Gỏi ngó sen\nTôm hấp sả\nSúp cua', 'Bản nháp cần kiểm tra nội dung trước khi công khai.',
 '/assets/meal.png','DRAFT',u.id
FROM users u WHERE u.email='chef.mai@gocook.test'
AND NOT EXISTS (SELECT 1 FROM products p WHERE p.name='A01 - Thực đơn mùa lễ (bản nháp)');

-- Admin/Order demonstration only. NO fabricated payment_transactions, ledger or order_escrows.
-- Dates are relative to execution time, so dashboard Day / Month / Year always has recent examples.
INSERT INTO orders(order_code,customer_name,customer_email,customer_id,product_name,
  chef_name,chef_id,service_address,customer_phone,guest_count,scheduled_at,
  total_amount,status,customer_note,internal_note)
SELECT v.code, customer.username, customer.email, customer.id, product.name,
  chef.username, chef.id,v.address,v.phone,v.guests,v.scheduled_at,v.total,v.status,v.note,v.internal_note
FROM (VALUES
 ('GC-A01-DEMO-001','customer.ngocanh@gocook.test','chef.mai@gocook.test','A01 - Mâm cơm Sum Vầy Miền Nam',4,(CURRENT_DATE - 15) + time '18:30',480000::numeric,'COMPLETED','Ít cay, không hành sống.','Demo: đơn đã hoàn thành; KHÔNG có giao dịch thật.','Bình Thạnh, TP. Hồ Chí Minh','0900000011'),
 ('GC-A01-DEMO-002','customer.minhthu@gocook.test','chef.binh@gocook.test','A01 - Tiệc Gia Đình Món Bắc',6,(CURRENT_DATE - 8) + time '17:30',1080000::numeric,'COMPLETED','Người lớn tuổi, cần ít dầu mỡ.','Demo: đơn đã hoàn thành; KHÔNG có giao dịch thật.','Thủ Đức, TP. Hồ Chí Minh','0900000012'),
 ('GC-A01-DEMO-003','customer.thanhha@gocook.test','chef.annhien@gocook.test','A01 - Mâm chay Thanh Lành',4,(CURRENT_DATE - 1) + time '11:30',420000::numeric,'COMPLETED','Không sử dụng hành/tỏi.','Demo: đơn đã hoàn thành; KHÔNG có giao dịch thật.','Quận 3, TP. Hồ Chí Minh','0900000013'),
 ('GC-A01-DEMO-004','customer.ngocanh@gocook.test','chef.binh@gocook.test','A01 - Tiệc Gia Đình Món Bắc',4,(CURRENT_DATE + 5) + time '18:00',720000::numeric,'PENDING','Cần chuẩn bị bàn ăn cho 4 người.','Demo: đang chờ xác nhận.','Quận 7, TP. Hồ Chí Minh','0900000011'),
 ('GC-A01-DEMO-005','customer.minhthu@gocook.test','chef.annhien@gocook.test','A01 - Mâm chay Thanh Lành',2,(CURRENT_DATE + 3) + time '12:00',210000::numeric,'CONFIRMED','Không ăn cay.','Demo: đã xác nhận, CHƯA có thanh toán thật.','Phú Nhuận, TP. Hồ Chí Minh','0900000012'),
 ('GC-A01-DEMO-006','customer.thanhha@gocook.test','chef.mai@gocook.test','A01 - Mâm cơm Sum Vầy Miền Nam',5,(CURRENT_DATE + 7) + time '19:00',600000::numeric,'PREPARING','Có trẻ nhỏ.','Demo: đơn đang chuẩn bị.','Gò Vấp, TP. Hồ Chí Minh','0900000013')
) AS v(code,customer_email,chef_email,product_name,guests,scheduled_at,total,status,note,internal_note,address,phone)
JOIN users customer ON customer.email=v.customer_email
JOIN users chef ON chef.email=v.chef_email
JOIN products product ON product.name=v.product_name
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.order_code=v.code);

COMMIT;
