-- GO Cook: durable per-account inbox. Backup DB first. Safe to re-run.
BEGIN;
CREATE TABLE IF NOT EXISTS public.app_notifications (
  id BIGSERIAL PRIMARY KEY,
  recipient_id BIGINT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  event_key VARCHAR(140) NOT NULL,
  type VARCHAR(40) NOT NULL CHECK (type IN
    ('ACCOUNT','BOOKING','PAYMENT','CHEF','SUPPORT','REQUEST','SYSTEM','PROMOTION')),
  title VARCHAR(180) NOT NULL,
  body VARCHAR(600) NOT NULL,
  action_path VARCHAR(260) NOT NULL DEFAULT '/notifications',
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  read_at TIMESTAMPTZ,
  CONSTRAINT uq_app_notifications_recipient_event UNIQUE (recipient_id,event_key),
  CONSTRAINT ck_app_notifications_path CHECK (left(action_path,1)='/' AND left(action_path,2)<>'//'),
  CONSTRAINT ck_app_notifications_read CHECK (NOT is_read OR read_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_app_notifications_unread
  ON public.app_notifications (recipient_id,created_at DESC,id DESC) WHERE is_read=false;
CREATE INDEX IF NOT EXISTS ix_app_notifications_history
  ON public.app_notifications (recipient_id,created_at DESC,id DESC);
CREATE TABLE IF NOT EXISTS public.notification_preferences (
  user_id BIGINT PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
  marketing_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS public.notification_campaigns (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT NOT NULL REFERENCES public.users(id),
  target_group VARCHAR(20) NOT NULL CHECK(target_group IN ('ALL','CUSTOMER','CHEF','ADMIN','COMPANY_ADMIN')),
  event_key VARCHAR(140) NOT NULL UNIQUE,
  type VARCHAR(40) NOT NULL CHECK(type IN ('SYSTEM','PROMOTION')),
  title VARCHAR(180) NOT NULL,
  body VARCHAR(600) NOT NULL,
  recipient_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_notification_campaign_actor ON public.notification_campaigns(actor_id,created_at DESC);

-- Event adapters: DB-triggered inbox events so the Booking/Payment owners do NOT
-- need to patch or call notification code. The event is committed with its business row.
-- Installation is intentionally AFTER base tables are in place; no retroactive demo spam.
CREATE OR REPLACE FUNCTION public.gocook_notify_order_change() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE changed BOOLEAN;
DECLARE ekey TEXT;
DECLARE label TEXT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    changed := TRUE;
  ELSE
    changed := NEW.status IS DISTINCT FROM OLD.status;
  END IF;
  IF NOT changed THEN RETURN NEW; END IF;
  ekey := 'order:' || NEW.id || ':tx:' || txid_current() || ':' || NEW.status;
  label := 'Đơn ' || COALESCE(NEW.order_code, NEW.id::text) || ' hiện ở trạng thái ' || NEW.status || '.';
  INSERT INTO public.app_notifications(recipient_id,event_key,type,title,body,action_path)
  SELECT u.id,ekey,'BOOKING','Cập nhật đơn hàng',label,'/orders'
  FROM public.users u WHERE u.id=NEW.customer_id AND u.is_active=true
  ON CONFLICT(recipient_id,event_key) DO NOTHING;
  IF NEW.chef_id IS NOT NULL AND NEW.chef_id IS DISTINCT FROM NEW.customer_id THEN
    INSERT INTO public.app_notifications(recipient_id,event_key,type,title,body,action_path)
    SELECT u.id,ekey,'BOOKING','Lịch nấu được cập nhật',label,'/seller/orders'
    FROM public.users u WHERE u.id=NEW.chef_id AND u.is_active=true
    ON CONFLICT(recipient_id,event_key) DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_gocook_notify_order_change ON public.orders;
CREATE TRIGGER trg_gocook_notify_order_change
  AFTER INSERT OR UPDATE OF status ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.gocook_notify_order_change();

CREATE OR REPLACE FUNCTION public.gocook_notify_payment_change() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE changed BOOLEAN;
DECLARE ekey TEXT;
DECLARE label TEXT;
BEGIN
  IF TG_OP='INSERT' THEN
    changed := TRUE;
  ELSE
    changed := NEW.status IS DISTINCT FROM OLD.status;
  END IF;
  IF NOT changed OR NEW.status NOT IN ('SUCCESS','FAILED','CANCELLED','REFUNDED') THEN RETURN NEW; END IF;
  ekey := 'payment:' || NEW.id || ':' || NEW.status;
  label := CASE NEW.status
    WHEN 'SUCCESS' THEN 'Thanh toán đã được ghi nhận. Vui lòng xem trạng thái đơn hàng.'
    WHEN 'REFUNDED' THEN 'Khoản thanh toán đã được ghi nhận hoàn tiền. Vui lòng kiểm tra chi tiết giao dịch.'
    WHEN 'FAILED' THEN 'Giao dịch thanh toán chưa thành công.'
    ELSE 'Lượt thanh toán đã hủy hoặc hết hạn.' END;
  INSERT INTO public.app_notifications(recipient_id,event_key,type,title,body,action_path)
  SELECT u.id,ekey,'PAYMENT','Cập nhật thanh toán',label,'/orders'
  FROM public.orders o JOIN public.users u ON u.id=o.customer_id
  WHERE o.id=NEW.order_id AND u.is_active=true
  ON CONFLICT(recipient_id,event_key) DO NOTHING;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_gocook_notify_payment_change ON public.payment_transactions;
CREATE TRIGGER trg_gocook_notify_payment_change
  AFTER INSERT OR UPDATE OF status ON public.payment_transactions
  FOR EACH ROW EXECUTE FUNCTION public.gocook_notify_payment_change();

-- Product publishing/hiding: notify the owning Chef without touching the Seller module.
CREATE OR REPLACE FUNCTION public.gocook_notify_menu_visibility() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE ekey TEXT;
BEGIN
  IF NEW.seller_id IS NULL THEN RETURN NEW; END IF;
  IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
    IF NEW.status IN ('PUBLISHED','HIDDEN') THEN
      ekey := 'menu:' || NEW.id || ':tx:' || txid_current() || ':' || NEW.status;
      INSERT INTO public.app_notifications(recipient_id,event_key,type,title,body,action_path)
      SELECT u.id,ekey,'CHEF',
        CASE WHEN NEW.status='PUBLISHED' THEN 'Thực đơn được hiển thị' ELSE 'Thực đơn đã ẩn' END,
        'Thực đơn ' || left(NEW.name,200) || ' đã chuyển trạng thái ' || NEW.status || '.',
        '/seller/products'
      FROM public.users u WHERE u.id=NEW.seller_id AND u.is_active=true
      ON CONFLICT(recipient_id,event_key) DO NOTHING;
    END IF;
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_gocook_notify_menu_visibility ON public.products;
CREATE TRIGGER trg_gocook_notify_menu_visibility
  AFTER UPDATE OF status ON public.products
  FOR EACH ROW EXECUTE FUNCTION public.gocook_notify_menu_visibility();

COMMIT;
