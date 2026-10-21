-- GO Cook: Dynamic navigation pages / FAQ and customer custom request intake.
-- Run after 04_admin_operations_schema.sql. Safe to repeat, never overwrites existing FAQs.
BEGIN;
CREATE TABLE IF NOT EXISTS public_faqs (
  id BIGSERIAL PRIMARY KEY,
  question VARCHAR(255) NOT NULL,
  answer VARCHAR(4000) NOT NULL,
  category VARCHAR(80) NOT NULL DEFAULT 'Chung',
  display_order INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_public_faqs_visible ON public_faqs(is_active,display_order,id);
INSERT INTO public_faqs(question,answer,category,display_order)
SELECT seed.question,seed.answer,seed.category,seed.display_order
FROM (VALUES
  ('GO Cook hoạt động như thế nào?', 'GO Cook kết nối khách hàng với đầu bếp để nấu tại địa điểm khách hàng lựa chọn. Chi phí và dịch vụ cụ thể cần xác nhận trước khi đặt.', 'Giới thiệu', 10),
  ('Giá thực đơn được tính như thế nào?', 'Trang thực đơn hiển thị giá theo người. Tổng chi phí phụ thuộc số khách và điều kiện được xác nhận trong quá trình đặt.', 'Thực đơn', 20),
  ('Tôi có thể thông báo thực phẩm bị dị ứng không?', 'Hãy ghi rõ các thực phẩm cần tránh trong phần yêu cầu nấu ăn và xác nhận lại với đầu bếp trước khi đặt.', 'An toàn thực phẩm', 30),
  ('Tôi muốn thay đổi hoặc hủy lịch thì làm sao?', 'Hãy liên hệ bộ phận hỗ trợ kèm mã đơn để được kiểm tra điều kiện áp dụng. Chính sách cụ thể phụ thuộc đơn hàng và quy định đang hiệu lực.', 'Đặt lịch', 40)
) AS seed(question,answer,category,display_order)
WHERE NOT EXISTS (SELECT 1 FROM public_faqs f WHERE f.question=seed.question);

CREATE TABLE IF NOT EXISTS public_cooking_requests (
  id BIGSERIAL PRIMARY KEY,
  request_code VARCHAR(40) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  desired_dishes VARCHAR(3000) NOT NULL,
  cuisine VARCHAR(100) NOT NULL,
  adults INTEGER NOT NULL CHECK(adults BETWEEN 1 AND 100),
  children INTEGER NOT NULL CHECK(children BETWEEN 0 AND 100),
  avoid_ingredients VARCHAR(2000),
  spice_level VARCHAR(50) NOT NULL,
  meal_date DATE NOT NULL,
  meal_time VARCHAR(30) NOT NULL,
  kitchen_address VARCHAR(500) NOT NULL,
  equipment_available BOOLEAN NOT NULL DEFAULT FALSE,
  budget_min NUMERIC(14,2) NOT NULL CHECK(budget_min > 0),
  budget_max NUMERIC(14,2) NOT NULL CHECK(budget_max >= budget_min),
  reference_image_url VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','REVIEWING','CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cooking_requests_customer ON public_cooking_requests(customer_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cooking_requests_status ON public_cooking_requests(status,created_at DESC);
COMMIT;
