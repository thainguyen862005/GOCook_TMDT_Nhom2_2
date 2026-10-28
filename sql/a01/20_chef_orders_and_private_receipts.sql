-- GO Cook Chef workflow: private ingredient receipt blobs and terminal booking release.
-- Run after sql/migrations/chef_migration_20261014.sql AND sql/a01/18_notifications_inbox.sql.
-- BACKUP your DB first. No changes to existing payments or escrow balances.
BEGIN;
SET LOCAL lock_timeout='10s';
CREATE TABLE IF NOT EXISTS public.chef_receipt_blobs (
  receipt_id BIGINT PRIMARY KEY REFERENCES public.order_ingredient_receipts(id) ON DELETE CASCADE,
  content_bytes BYTEA NOT NULL,
  CONSTRAINT ck_chef_receipt_size CHECK (octet_length(content_bytes) BETWEEN 1 AND 5242880)
);
-- When an order is terminal, release the calendar reservation; do not touch money.
CREATE OR REPLACE FUNCTION public.gocook_release_finished_chef_booking()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status
    AND NEW.status IN ('REJECTED','CANCELLED','COMPLETED') THEN
   UPDATE public.chef_bookings SET booking_status='RELEASED',updated_at=now()
   WHERE order_id=NEW.id AND booking_status='ACTIVE';
 END IF;
 RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS trg_gocook_release_finished_chef_booking ON public.orders;
CREATE TRIGGER trg_gocook_release_finished_chef_booking
AFTER UPDATE OF status ON public.orders
FOR EACH ROW EXECUTE FUNCTION public.gocook_release_finished_chef_booking();
COMMIT;
