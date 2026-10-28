-- GO Cook | read-only post-migration verification; run in Navicat, not in application boot.
SELECT current_database() AS database_name,
       current_schema() AS schema_name,
       to_regclass('public.orders') AS orders_table,
       EXISTS (
         SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'orders' AND column_name = 'service_type'
       ) AS has_service_type;

-- Run the following only if has_service_type = true.
SELECT service_type, COUNT(*) AS order_count
FROM public.orders
GROUP BY service_type
ORDER BY service_type;

SELECT COUNT(*) AS invalid_service_types
FROM public.orders
WHERE service_type IS NULL OR service_type NOT IN ('MENU','BUDGET');

SELECT o.order_code, o.service_type, o.product_name, o.status
FROM public.orders o ORDER BY o.id DESC LIMIT 20;
