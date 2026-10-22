-- Proposal Sprint 1: remember the booking an accepted proposal produced, and guard "one winner".
-- Only ADDs columns and indexes; re-runnable. Run after sql/migrations/proposal_migration_20261021.sql.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '1min';

-- Set when the proposal is ACCEPTED; a repeated accept returns these instead of booking again.
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS accepted_booking_id BIGINT;
ALTER TABLE proposals ADD COLUMN IF NOT EXISTS accepted_order_code VARCHAR(50);

-- At most one ACCEPTED proposal per custom request, even if two accepts ever race past the row locks.
CREATE UNIQUE INDEX IF NOT EXISTS ux_proposals_one_accepted_per_request ON proposals(custom_request_id)
  WHERE status = 'ACCEPTED';

-- Customer comparison list: all proposals of one request, filtered by status.
CREATE INDEX IF NOT EXISTS idx_proposals_request_status ON proposals(custom_request_id, status);

COMMIT;
