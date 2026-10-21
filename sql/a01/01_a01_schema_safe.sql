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
