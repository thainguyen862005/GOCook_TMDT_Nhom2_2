-- Mandatory for request matching V2. Non-destructive / safe to rerun.
-- First run: 20261029_main_workflow.sql and 20261029_order_chat_workflow.sql.
BEGIN;
ALTER TABLE public.public_cooking_requests
  ADD COLUMN IF NOT EXISTS requested_area VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_public_cooking_requests_area
  ON public.public_cooking_requests(requested_area);
COMMIT;
