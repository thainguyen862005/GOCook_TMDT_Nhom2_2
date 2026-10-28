-- GO Cook | Notification migration 18 preflight (read-only).
-- Run this in Navicat against the SAME database Spring Boot uses.
SELECT current_database() AS database_name, current_schema() AS current_schema;

WITH expected(table_name, column_name) AS (
    VALUES
      ('users','id'),('users','is_active'),
      ('roles','id'),('roles','role_name'),
      ('user_roles','user_id'),('user_roles','role_id'),('user_roles','is_active'),
      ('orders','id'),('orders','order_code'),('orders','customer_id'),
      ('orders','chef_id'),('orders','status'),
      ('payment_transactions','id'),('payment_transactions','order_id'),('payment_transactions','status'),
      ('products','id'),('products','seller_id'),('products','name'),('products','status')
)
SELECT e.table_name, e.column_name,
       CASE WHEN c.column_name IS NULL THEN 'MISSING' ELSE 'OK' END AS precheck_status
FROM expected e
LEFT JOIN information_schema.columns c
  ON c.table_schema='public' AND c.table_name=e.table_name AND c.column_name=e.column_name
ORDER BY e.table_name, e.column_name;

-- All rows must say OK. If not, apply the owning module's migration first;
-- do NOT use a table-creation workaround that guesses the module's schema.
