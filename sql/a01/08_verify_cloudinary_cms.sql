-- Read-only Navicat verification after 07_site_content_cloudinary.sql
SELECT id, version, site_name, hero_title, hero_image_url, logo_url, updated_by, updated_at
FROM public.site_content WHERE id = 1;
SELECT product_id, COUNT(*) AS photo_count,
       SUM(CASE WHEN is_cover THEN 1 ELSE 0 END) AS cover_count
FROM public.product_images WHERE is_active = true
GROUP BY product_id ORDER BY product_id;
-- Successful photos should show HTTPS res.cloudinary.com URLs (old /assets paths may remain).
SELECT id, product_id, cloudinary_public_id, image_url
FROM public.product_images WHERE is_active = true
ORDER BY product_id, display_order LIMIT 20;
