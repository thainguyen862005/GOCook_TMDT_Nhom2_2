-- Proposal model (Sprint 0): a Chef's priced offer for a customer's custom request.
-- Creates only the table + indexes; no business logic. Re-runnable.
-- Run after sql/migrations/chef_migration_20261014.sql (users table is from the base schema).
--
-- custom_request_id has NO foreign key because custom_requests does not exist yet.
-- TODO (Thai, when creating custom_requests):
--   ALTER TABLE proposals ADD CONSTRAINT fk_proposals_custom_request
--     FOREIGN KEY (custom_request_id) REFERENCES custom_requests(id);
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

CREATE TABLE IF NOT EXISTS proposals (
    id BIGSERIAL PRIMARY KEY,
    custom_request_id BIGINT NOT NULL,
    chef_id BIGINT NOT NULL REFERENCES users(id),
    proposed_price NUMERIC(14,2) NOT NULL CHECK (proposed_price > 0),
    dishes TEXT,
    note TEXT,
    expires_at TIMESTAMPTZ,
    -- starts at 1; the service increments it every time the Chef edits the proposal
    revision INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SENT','REVISED','WITHDRAWN','ACCEPTED','REJECTED','EXPIRED')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);

-- One live proposal per (request, chef). Closed ones (WITHDRAWN/ACCEPTED/REJECTED/EXPIRED) do not count.
CREATE UNIQUE INDEX IF NOT EXISTS ux_proposals_live_per_chef ON proposals(custom_request_id, chef_id)
  WHERE status IN ('DRAFT','SENT','REVISED');
CREATE INDEX IF NOT EXISTS idx_proposals_chef ON proposals(chef_id, status);

COMMIT;
