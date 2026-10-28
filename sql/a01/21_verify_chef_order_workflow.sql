-- Read-only checks after Chef migration and SQL 20.
SELECT to_regclass('public.orders') AS orders_table,
       to_regclass('public.chef_bookings') AS chef_bookings,
       to_regclass('public.seller_schedules') AS availability,
       to_regclass('public.order_escrows') AS escrow,
       to_regclass('public.order_ingredient_receipts') AS receipt_meta,
       to_regclass('public.chef_receipt_blobs') AS private_receipts;
SELECT tgname FROM pg_trigger WHERE tgrelid='public.orders'::regclass
 AND NOT tgisinternal AND tgname IN
 ('trg_gocook_notify_order_change','trg_gocook_release_finished_chef_booking');
SELECT status,count(*) FROM orders GROUP BY status ORDER BY status;
SELECT booking_status,count(*) FROM chef_bookings GROUP BY booking_status;
SELECT count(*) AS receipt_count FROM chef_receipt_blobs;
