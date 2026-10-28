-- GO Cook | Read-only verification after 18_notifications_inbox.sql.
SELECT current_database() AS database_name,
       to_regclass('public.app_notifications') AS notifications_table,
       to_regclass('public.notification_preferences') AS preferences_table,
       to_regclass('public.notification_campaigns') AS campaigns_table;

-- All 3 triggers should exist and be enabled. These only fire for NEW changes.
SELECT expected.trigger_name,
       CASE WHEN t.oid IS NULL THEN 'MISSING'
            WHEN t.tgenabled <> 'O' THEN 'DISABLED' ELSE 'OK' END AS status
FROM (VALUES ('trg_gocook_notify_order_change'),
             ('trg_gocook_notify_payment_change'),
             ('trg_gocook_notify_menu_visibility')) AS expected(trigger_name)
LEFT JOIN pg_trigger t ON t.tgname=expected.trigger_name AND NOT t.tgisinternal
ORDER BY expected.trigger_name;

SELECT type, COUNT(*) AS notifications FROM public.app_notifications
GROUP BY type ORDER BY type;
SELECT COUNT(*) AS orphan_notifications
FROM public.app_notifications n
LEFT JOIN public.users u ON u.id=n.recipient_id
WHERE u.id IS NULL;
SELECT COUNT(*) AS duplicate_events FROM (
    SELECT recipient_id, event_key FROM public.app_notifications
    GROUP BY recipient_id, event_key HAVING COUNT(*) > 1
) AS duplicates;

-- Expected: three OK triggers, 0 orphan_notifications, 0 duplicate_events.
-- No rows are modified; no seed or historical notifications are generated.
