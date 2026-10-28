-- GO Cook: safe READ-ONLY verification after migration 23.
-- Does not call any function or modify orders/escrow.
SELECT current_database() AS database_name, current_schema() AS current_schema,
       current_user AS connected_db_role, current_setting('search_path') AS search_path;

WITH checks(name, obj) AS (
  SELECT 'gocook_register_verified_payment(bigint,bigint)',
         to_regprocedure('public.gocook_register_verified_payment(bigint,bigint)') UNION ALL
  SELECT 'gocook_accept_and_release(bigint,bigint)',
         to_regprocedure('public.gocook_accept_and_release(bigint,bigint)') UNION ALL
  SELECT 'gocook_chef_reject_order(bigint,bigint,text)',
         to_regprocedure('public.gocook_chef_reject_order(bigint,bigint,text)')
)
SELECT name, CASE WHEN obj IS NULL THEN 'MISSING' ELSE 'OK' END AS status,
       CASE WHEN obj IS NULL THEN NULL ELSE has_function_privilege(current_user, obj, 'EXECUTE') END AS can_execute_as_current_role
FROM checks ORDER BY name;

-- Make sure server-side permissions allow the backend DB role to execute these functions.
-- If Navicat and Spring Boot use different roles, check using the backend role in pg_roles.
-- Never invoke functions with sample order IDs to 'verify' them.
