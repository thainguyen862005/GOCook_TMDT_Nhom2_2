-- READ-ONLY pre-deployment verification for GO Cook Chef UX/matching/reviews.
-- Run against the active application database; the uploaded public.sql is a destructive export, NOT a migration.
SELECT current_database() AS database_name,
       to_regclass('public.chef_profiles') AS chef_profiles_table,
       to_regclass('public.orders') AS orders_table,
       to_regclass('public.order_reviews') AS order_reviews_table,
       to_regclass('public.refunds') AS refunds_table;

-- Chef profiles that will NOT match an exact province code until migrated.
SELECT count(*) AS active_chef_profiles,
       count(*) FILTER (WHERE service_area IS NULL OR btrim(service_area) = '') AS missing_area,
       count(*) FILTER (WHERE service_area IS NOT NULL AND service_area !~ '^[0-9]{2}(,[0-9]{2})*$') AS legacy_free_text_areas,
       count(*) FILTER (WHERE service_area ~ '^[0-9]{2}(,[0-9]{2})*$') AS normalized_areas
FROM public.chef_profiles
WHERE is_active = true;

-- Booking and payment status distributions (counts only, no customer PII).
SELECT status, count(*) AS orders FROM public.orders GROUP BY status ORDER BY status;
SELECT status, count(*) AS escrows FROM public.order_escrows GROUP BY status ORDER BY status;
SELECT status, count(*) AS refunds FROM public.refunds GROUP BY status ORDER BY status;

-- Only after verifying that order_reviews exists in first SELECT, run this separately:
-- SELECT count(*) AS review_count FROM public.order_reviews;
