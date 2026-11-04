SELECT p.id AS "id", p.name AS "name", p.category AS "category",
       COALESCE(p.dish_items, '') AS "dishItems",
       COALESCE(p.description, '') AS "description"
FROM public.products p
WHERE p.is_active = TRUE AND p.status = 'PUBLISHED' AND p.seller_id IS NOT NULL
ORDER BY p.id;
