SELECT
    o.customer_id AS "user_id",
    oi.product_id AS "menu_id",
    to_char(
            to_timestamp(o.created_at / 1000.0) AT TIME ZONE 'UTC',
            'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'
    ) AS "event_time"
FROM public.orders o
         JOIN public.order_items oi ON oi.order_id = o.id
         JOIN public.order_escrows e ON e.order_id = o.id
WHERE o.service_type = 'MENU'
  AND o.status = 'COMPLETED'
  AND e.status = 'RELEASED'
  AND oi.product_id IS NOT NULL
ORDER BY o.customer_id, o.created_at, o.id;
