-- GO Cook: read-only preflight for Chef workflow SQL 20.
-- In Navicat, first run ROLLBACK; as its own statement if a prior transaction failed.
-- Then execute this query. Every row for a required dependency must report OK.
-- This file does not create, update or delete any data.
SELECT current_database() AS database_name,
       current_schema() AS active_schema,
       current_setting('search_path') AS search_path;

WITH requirements(object_name, kind, exists_now) AS (
  VALUES
   ('public.users', 'table', to_regclass('public.users') IS NOT NULL),
   ('public.orders', 'table', to_regclass('public.orders') IS NOT NULL),
   ('public.chef_profiles', 'table', to_regclass('public.chef_profiles') IS NOT NULL),
   ('public.seller_schedules', 'table', to_regclass('public.seller_schedules') IS NOT NULL),
   ('public.chef_bookings', 'table', to_regclass('public.chef_bookings') IS NOT NULL),
   ('public.order_ingredient_receipts', 'table', to_regclass('public.order_ingredient_receipts') IS NOT NULL),
   ('public.order_escrows', 'table', to_regclass('public.order_escrows') IS NOT NULL),
   ('public.app_notifications', 'table', to_regclass('public.app_notifications') IS NOT NULL),
   ('public.orders.status', 'column', EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='orders' AND column_name='status')),
   ('public.chef_bookings.booking_status', 'column', EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='chef_bookings' AND column_name='booking_status')),
   ('public.chef_bookings.updated_at', 'column', EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='chef_bookings' AND column_name='updated_at')),
   ('public.order_ingredient_receipts.id', 'column', EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='order_ingredient_receipts' AND column_name='id'))
)
SELECT object_name,kind,CASE WHEN exists_now THEN 'OK' ELSE 'MISSING' END AS result
FROM requirements ORDER BY kind,object_name;

-- Check whether SQL 20 already completed; if both exist, do not rerun unnecessarily.
SELECT to_regclass('public.chef_receipt_blobs') AS private_receipt_table,
       EXISTS (
         SELECT 1 FROM pg_trigger
         WHERE tgrelid=to_regclass('public.orders') AND NOT tgisinternal
           AND tgname='trg_gocook_release_finished_chef_booking'
       ) AS release_trigger_exists;
