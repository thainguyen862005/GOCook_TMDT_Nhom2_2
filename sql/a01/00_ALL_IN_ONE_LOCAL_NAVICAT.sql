-- GO Cook Admin local/dev one-click Navicat setup. BACKUP DB FIRST!
-- NEVER run against production: contains demo users and passwords.


-- ========== FILE: 01_a01_schema_safe.sql ==========

-- GO Cook A01 / PostgreSQL 16 / Navicat / app_db
-- Safe for EXISTING LOCAL DB: additive & idempotent. It NEVER drops a TABLE or deletes rows.
-- BACKUP FIRST. Run this ONE script to completion before 02_a01_demo_seed.sql.
-- Do not run concurrently with a migration, and do not use against production unchecked.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';

-- Roles and existing auth tables (for databases that were not initialized from schema_db).
CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  avatar_url VARCHAR(255),
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);
CREATE TABLE IF NOT EXISTS roles (
  id BIGSERIAL PRIMARY KEY,
  role_name VARCHAR(40) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);
CREATE TABLE IF NOT EXISTS user_roles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;

INSERT INTO roles(role_name)
SELECT v.role_name FROM (VALUES ('USER'),('INSTRUCTOR'),('ADMIN'),('COMPANY_ADMIN')) v(role_name)
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.role_name = v.role_name);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_active ON user_roles(user_id,is_active);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_active ON user_roles(role_id,is_active);

-- Admin CRUD product schema. Chef ownership column is needed by current Product entity.
CREATE TABLE IF NOT EXISTS products (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(100) NOT NULL,
  unit_price NUMERIC(14,2) NOT NULL,
  min_guests INTEGER NOT NULL,
  max_guests INTEGER NOT NULL,
  dish_items TEXT,
  description TEXT,
  image_url VARCHAR(1000),
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  seller_id BIGINT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);
ALTER TABLE products ADD COLUMN IF NOT EXISTS dish_items TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000);
ALTER TABLE products ADD COLUMN IF NOT EXISTS seller_id BIGINT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
UPDATE products SET status = CASE WHEN is_active THEN 'DRAFT' ELSE 'HIDDEN' END WHERE status IS NULL;
ALTER TABLE products ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE products ALTER COLUMN status SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_updated_at ON products(updated_at DESC);

-- Admin CRUD order schema. Money and status history are NOT synthesized here.
CREATE TABLE IF NOT EXISTS orders (
  id BIGSERIAL PRIMARY KEY,
  order_code VARCHAR(50) NOT NULL UNIQUE,
  customer_name VARCHAR(255) NOT NULL,
  customer_email VARCHAR(255),
  customer_id BIGINT,
  product_name VARCHAR(255) NOT NULL,
  chef_name VARCHAR(255),
  chef_id BIGINT,
  service_address VARCHAR(1000),
  customer_phone VARCHAR(30),
  guest_count INTEGER NOT NULL,
  scheduled_at TIMESTAMP NOT NULL,
  total_amount NUMERIC(14,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  customer_note TEXT,
  internal_note TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_email VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS chef_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS chef_name VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_address VARCHAR(1000);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_note TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS internal_note TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR(30);
UPDATE orders SET status = 'PENDING' WHERE status IS NULL;
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE orders ALTER COLUMN status SET NOT NULL;
-- The old admin_management status CHECK only supports five values; extend it for current Java enum.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='orders'::regclass AND conname='chk_orders_status_chef') THEN
    ALTER TABLE orders ADD CONSTRAINT chk_orders_status_chef CHECK (
      status IN ('PENDING','CONFIRMED','PREPARING','COOKING','AWAITING_ACCEPTANCE','COMPLETED','CANCELLED','REJECTED'));
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_scheduled_at ON orders(scheduled_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_chef_id ON orders(chef_id);

-- Minimal chef_profile backing for demo data, same shape as existing chef migration.
CREATE TABLE IF NOT EXISTS chef_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
  display_name VARCHAR(255) NOT NULL,
  bio TEXT,
  phone VARCHAR(30),
  service_area VARCHAR(500),
  rating NUMERIC(3,2) NOT NULL DEFAULT 0,
  is_verified BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);

COMMIT;

-- Run the non-mutating inspection query 03_a01_verify.sql after seeding.


-- ========== FILE: 04_admin_operations_schema.sql ==========

-- GO Cook - Admin Operations v1, PostgreSQL 16
-- Execute AFTER 01_a01_schema_safe.sql. Additive migration; no DROP, no TRUNCATE.
-- BACKUP current database before executing in Navicat. NOT for production without review.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';

-- Payment projection depends on the payment-owned table; define only missing base structure.
-- Existing payment migrations and gateway code remain authoritative for its rows.
CREATE TABLE IF NOT EXISTS payment_transactions (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  provider VARCHAR(50) NOT NULL,
  transaction_code VARCHAR(255),
  amount NUMERIC(14,2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  paid_at TIMESTAMPTZ,
  raw_reference TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now())*1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now())*1000)::bigint
);
CREATE INDEX IF NOT EXISTS idx_admin_payment_order ON payment_transactions(order_id, status);
CREATE INDEX IF NOT EXISTS idx_admin_payment_paid_at ON payment_transactions(paid_at DESC);

ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS service_area VARCHAR(500);
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS rating NUMERIC(3,2) NOT NULL DEFAULT 0;
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS verification_status VARCHAR(25) NOT NULL DEFAULT 'PENDING';
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS verification_note VARCHAR(1000);
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE chef_profiles ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
UPDATE chef_profiles SET verification_status = 'APPROVED' WHERE is_verified AND verification_status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_admin_chef_verified ON chef_profiles(verification_status, is_active);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT,
  action VARCHAR(70) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id BIGINT,
  details VARCHAR(1500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_time ON admin_audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_entity ON admin_audit_logs(entity_type, entity_id);

CREATE TABLE IF NOT EXISTS admin_chef_verifications (
  id BIGSERIAL PRIMARY KEY,
  chef_id BIGINT NOT NULL REFERENCES users(id),
  reviewer_id BIGINT REFERENCES users(id),
  old_status VARCHAR(25) NOT NULL,
  new_status VARCHAR(25) NOT NULL CHECK (new_status IN ('PENDING','APPROVED','REJECTED','MORE_INFO')),
  reason VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_admin_verification_chef ON admin_chef_verifications(chef_id, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_vouchers (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  description VARCHAR(300),
  discount_type VARCHAR(12) NOT NULL CHECK(discount_type IN ('PERCENT','FIXED')),
  discount_value NUMERIC(14,2) NOT NULL CHECK(discount_value > 0),
  min_order_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(min_order_amount >= 0),
  max_discount NUMERIC(14,2),
  usage_limit INTEGER NOT NULL DEFAULT 100 CHECK(usage_limit > 0),
  used_count INTEGER NOT NULL DEFAULT 0 CHECK(used_count >= 0),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
  is_active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_admin_voucher_valid ON admin_vouchers(is_active, starts_at, ends_at);

CREATE TABLE IF NOT EXISTS admin_cases (
  id BIGSERIAL PRIMARY KEY,
  case_code VARCHAR(40) NOT NULL UNIQUE,
  subject VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  booking_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
  customer_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  chef_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK(priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  status VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','CLOSED')),
  resolution TEXT,
  internal_note TEXT,
  assignee_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_admin_cases_status ON admin_cases(status, priority, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_refund_requests (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','REVIEWED','REJECTED','EXECUTED')),
  decision_note VARCHAR(1000),
  reviewed_by BIGINT REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  gateway_reference VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_admin_refund_status ON admin_refund_requests(status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_refund_one_pending ON admin_refund_requests(order_id) WHERE status='PENDING';

-- Moderation inbox only. This table does NOT change public review visibility until Review domain integrates it.
CREATE TABLE IF NOT EXISTS admin_review_reports (
  id BIGSERIAL PRIMARY KEY,
  external_review_id BIGINT,
  reporter_id BIGINT REFERENCES users(id),
  reason VARCHAR(1000) NOT NULL,
  evidence_url VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','HIDE_REQUESTED','DISMISSED')),
  decision_note VARCHAR(1000),
  reviewer_id BIGINT REFERENCES users(id),
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_admin_reviews_status ON admin_review_reports(status, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_tickets (
  id BIGSERIAL PRIMARY KEY,
  ticket_code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  contact_email VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_PROGRESS','ANSWERED','CLOSED')),
  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK(priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  assigned_to BIGINT REFERENCES users(id),
  internal_note TEXT,
  response TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_admin_tickets_status ON admin_tickets(status, created_at DESC);
COMMIT;


-- ========== FILE: 02_a01_demo_seed.sql ==========

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


-- ========== FILE: 05_admin_operations_demo.sql ==========

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


-- ========== FILE: 03_a01_verify.sql ==========

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


-- ========== FILE: 06_admin_operations_verify.sql ==========

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
