-- GO Cook: read-only final Chef/Notifications database audit.
-- Execute in Navicat against the SAME PostgreSQL database used by Spring Boot.
-- No schema or data changes. ALL rows in checks must be OK before accepting the PR.
SELECT current_database() AS database_name, current_schema() AS current_schema, current_setting('search_path') AS search_path;

WITH checks(name, ok) AS (
 SELECT 'table:users', to_regclass('public.users') IS NOT NULL UNION ALL
 SELECT 'table:orders', to_regclass('public.orders') IS NOT NULL UNION ALL
 SELECT 'table:order_escrows', to_regclass('public.order_escrows') IS NOT NULL UNION ALL
 SELECT 'table:escrow_ledger_entries', to_regclass('public.escrow_ledger_entries') IS NOT NULL UNION ALL
 SELECT 'table:chef_bookings', to_regclass('public.chef_bookings') IS NOT NULL UNION ALL
 SELECT 'table:seller_schedules', to_regclass('public.seller_schedules') IS NOT NULL UNION ALL
 SELECT 'table:order_status_history', to_regclass('public.order_status_history') IS NOT NULL UNION ALL
 SELECT 'table:order_ingredient_receipts', to_regclass('public.order_ingredient_receipts') IS NOT NULL UNION ALL
 SELECT 'table:chef_receipt_blobs', to_regclass('public.chef_receipt_blobs') IS NOT NULL UNION ALL
 SELECT 'table:app_notifications', to_regclass('public.app_notifications') IS NOT NULL UNION ALL
 SELECT 'table:notification_campaigns', to_regclass('public.notification_campaigns') IS NOT NULL UNION ALL
 SELECT 'table:notification_preferences', to_regclass('public.notification_preferences') IS NOT NULL UNION ALL
 SELECT 'function:gocook_register_verified_payment(bigint,bigint)', to_regprocedure('public.gocook_register_verified_payment(bigint,bigint)') IS NOT NULL UNION ALL
 SELECT 'function:gocook_accept_and_release(bigint,bigint)', to_regprocedure('public.gocook_accept_and_release(bigint,bigint)') IS NOT NULL UNION ALL
 SELECT 'function:gocook_chef_reject_order(bigint,bigint,text)', to_regprocedure('public.gocook_chef_reject_order(bigint,bigint,text)') IS NOT NULL UNION ALL
 SELECT 'function:gocook_release_finished_chef_booking()', to_regprocedure('public.gocook_release_finished_chef_booking()') IS NOT NULL UNION ALL
 SELECT 'trigger:notify_order', EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid=to_regclass('public.orders') AND tgname='trg_gocook_notify_order_change' AND tgenabled='O' AND NOT tgisinternal) UNION ALL
 SELECT 'trigger:notify_payment', EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid=to_regclass('public.payment_transactions') AND tgname='trg_gocook_notify_payment_change' AND tgenabled='O' AND NOT tgisinternal) UNION ALL
 SELECT 'trigger:notify_menu', EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid=to_regclass('public.products') AND tgname='trg_gocook_notify_menu_visibility' AND tgenabled='O' AND NOT tgisinternal) UNION ALL
 SELECT 'trigger:release_chef_booking', EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid=to_regclass('public.orders') AND tgname='trg_gocook_release_finished_chef_booking' AND tgenabled='O' AND NOT tgisinternal) UNION ALL
 SELECT 'constraint:chef_booking_no_overlap', EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid=to_regclass('public.chef_bookings') AND conname='ex_chef_bookings_no_overlap' AND contype='x') UNION ALL
 SELECT 'constraint:notification_idempotency', EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid=to_regclass('public.app_notifications') AND conname='uq_app_notifications_recipient_event') UNION ALL
 SELECT 'column:orders.chef_completed_at', EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='orders' AND column_name='chef_completed_at') UNION ALL
 SELECT 'column:orders.service_duration_minutes', EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='orders' AND column_name='service_duration_minutes')
)
SELECT name, CASE WHEN ok THEN 'OK' ELSE 'MISSING' END AS result FROM checks ORDER BY name;

-- Do not use a dump with DROP TABLE statements as a migration.
-- If a function is missing, coordinate with the Payment/Booking owner to apply the reviewed
-- function definition from sql/migrations/chef_migration_20261014.sql.
-- Never create fictitious SUCCESS payments, HELD escrows or balances to pass this audit.
