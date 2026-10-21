-- GO Cook / Navicat PostgreSQL 16 - safe additive CMS migration
-- Run after base schema and Admin Operations. Back up database before migration.
BEGIN;
CREATE TABLE IF NOT EXISTS public.site_content (
  id SMALLINT PRIMARY KEY CHECK(id = 1),
  version BIGINT NOT NULL DEFAULT 0,
  site_name VARCHAR(100) NOT NULL,
  tagline VARCHAR(150) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  hero_title VARCHAR(200) NOT NULL,
  hero_subtitle VARCHAR(1000) NOT NULL,
  hero_image_url VARCHAR(1000) NOT NULL,
  hero_image_alt VARCHAR(200) NOT NULL,
  promo_image_url VARCHAR(1000) NOT NULL DEFAULT '',
  logo_url VARCHAR(1000) NOT NULL DEFAULT '',
  contact_email VARCHAR(254) NOT NULL DEFAULT '',
  contact_phone VARCHAR(30) NOT NULL DEFAULT '',
  facebook_url VARCHAR(500) NOT NULL DEFAULT '',
  instagram_url VARCHAR(500) NOT NULL DEFAULT '',
  footer_copyright VARCHAR(160) NOT NULL,
  updated_by BIGINT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO public.site_content(id,version,site_name,tagline,description,hero_title,hero_subtitle,
  hero_image_url,hero_image_alt,promo_image_url,logo_url,footer_copyright)
VALUES (1,0,'GO Cook','Đầu bếp tại gia',
  'Kết nối đầu bếp và gia đình Việt. Bữa cơm tươi ngon, được chăm chút ngay trong gian bếp của bạn.',
  'Đầu bếp bạn chọn. Bữa ăn bạn muốn. Ngay tại nhà.',
  'Một bữa cơm ngon, một buổi tối thảnh thơi. GO Cook kết nối bạn với đầu bếp tận tâm.',
  '/assets/hero.png','Đầu bếp nấu ăn tại gia đình','/assets/meal.png','', '© 2026 GO Cook')
ON CONFLICT (id) DO NOTHING;
-- Existing product_images table is owned by Seller; Admin reuses it, no duplicate gallery table.

-- If the Chef gallery migration has not been applied yet, provision its shared gallery table.
-- This matches product_images in sql/migrations/chef_migration_20261014.sql.
CREATE TABLE IF NOT EXISTS public.product_images (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES public.products(id) ON DELETE CASCADE,
  image_url VARCHAR(2000) NOT NULL,
  cloudinary_public_id VARCHAR(500),
  alt_text VARCHAR(255),
  display_order INT NOT NULL DEFAULT 0 CHECK (display_order >= 0),
  is_cover BOOLEAN NOT NULL DEFAULT false,
  is_active BOOLEAN NOT NULL DEFAULT true,
  version BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
  updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
);
CREATE INDEX IF NOT EXISTS idx_product_images_product
  ON public.product_images(product_id, display_order, id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_images_cover
  ON public.product_images(product_id) WHERE is_cover = true AND is_active = true;

COMMIT;
SELECT id, version, site_name, hero_image_url, updated_at FROM public.site_content;
