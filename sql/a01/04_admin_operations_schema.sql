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
