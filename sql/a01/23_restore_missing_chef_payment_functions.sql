-- GO Cook: restore the three missing Chef / Payment database functions.
-- Based on sql/migrations/chef_migration_20261014.sql in the uploaded GO Cook source.
-- Scope: CREATE FUNCTIONS ONLY. No INSERT/UPDATE/DELETE on business tables.
-- Do NOT run this file after a failed transaction without first executing ROLLBACK;
-- Backup database and confirm you are connected to the same DB used by Spring Boot.
-- Idempotent: if a function already exists with the exact signature, its body is NOT overwritten.
-- IMPORTANT: these functions change order/escrow data WHEN CALLED by the backend;
-- merely installing them does not execute those business actions.

BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '2min';

-- Fail early if the DB structure is not compatible with the source migration.
DO $preflight$
DECLARE
  v_table text;
  v_column text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'users','orders','payment_transactions','order_escrows',
    'escrow_ledger_entries','order_status_history','chef_bookings'] LOOP
    IF to_regclass('public.' || v_table) IS NULL THEN
      RAISE EXCEPTION 'Missing required table: public.% — stop and apply table migration first.', v_table;
    END IF;
  END LOOP;

  FOR v_table, v_column IN
    SELECT split_part(c.item, '.', 1), split_part(c.item, '.', 2)
    FROM unnest(ARRAY[
      'orders.id','orders.customer_id','orders.chef_id','orders.status',
      'orders.total_amount','orders.accepted_at','orders.rejected_reason',
      'orders.version','orders.updated_at',
      'payment_transactions.id','payment_transactions.order_id',
      'payment_transactions.status','payment_transactions.paid_at',
      'payment_transactions.amount',
      'order_escrows.id','order_escrows.order_id','order_escrows.chef_id',
      'order_escrows.payment_transaction_id','order_escrows.gross_amount',
      'order_escrows.platform_fee','order_escrows.chef_amount',
      'order_escrows.status','order_escrows.released_at','order_escrows.updated_at',
      'escrow_ledger_entries.order_id','escrow_ledger_entries.escrow_id',
      'escrow_ledger_entries.entry_type','escrow_ledger_entries.gross_amount',
      'escrow_ledger_entries.chef_amount','escrow_ledger_entries.platform_fee',
      'escrow_ledger_entries.note',
      'order_status_history.order_id','order_status_history.actor_user_id',
      'order_status_history.previous_status','order_status_history.new_status',
      'order_status_history.note',
      'chef_bookings.order_id','chef_bookings.booking_status',
      'chef_bookings.updated_at'
     ]) AS c(item)
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = v_table AND column_name = v_column
    ) THEN
      RAISE EXCEPTION 'Missing required column public.%.% — stop.', v_table, v_column;
    END IF;
  END LOOP;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.order_escrows'::regclass
      AND conname = 'order_escrows_order_id_key' AND contype = 'u'
  ) THEN
    RAISE EXCEPTION 'Missing unique constraint order_escrows_order_id_key — stop.';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.escrow_ledger_entries'::regclass
      AND conname = 'uq_escrow_ledger_order_event' AND contype = 'u'
  ) THEN
    RAISE EXCEPTION 'Missing unique constraint uq_escrow_ledger_order_event — stop.';
  END IF;
END $preflight$;

-- Install gocook_register_verified_payment only if absent; never replace modified implementation.
DO $install$
BEGIN
  IF to_regprocedure('public.gocook_register_verified_payment(bigint,bigint)') IS NULL THEN
    EXECUTE $install_sql$
CREATE FUNCTION public.gocook_register_verified_payment(
    p_order_id BIGINT, p_payment_transaction_id BIGINT
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $function$

DECLARE
    v_order orders%ROWTYPE;
    v_payment payment_transactions%ROWTYPE;
    v_escrow_id BIGINT;
    v_fee NUMERIC(14,2);
BEGIN
    SELECT * INTO v_order FROM orders WHERE id=p_order_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Order % not found',p_order_id; END IF;
    SELECT * INTO v_payment FROM payment_transactions WHERE id=p_payment_transaction_id FOR UPDATE;
    IF NOT FOUND OR v_payment.order_id <> p_order_id
        OR v_payment.status <> 'SUCCESS' OR v_payment.paid_at IS NULL THEN
        RAISE EXCEPTION 'Missing verified successful payment for order %',p_order_id;
    END IF;
    IF v_order.chef_id IS NULL OR v_order.customer_id IS NULL THEN
        RAISE EXCEPTION 'Assign chef_id and customer_id before payment escrow for order %',p_order_id;
    END IF;
    IF v_order.status <> 'PENDING' THEN
        RAISE EXCEPTION 'New paid order must be PENDING (actual: %)',v_order.status;
    END IF;
    IF v_payment.amount <> v_order.total_amount THEN
        RAISE EXCEPTION 'Payment mismatch for order %',p_order_id;
    END IF;
    IF EXISTS (SELECT 1 FROM order_escrows WHERE order_id=p_order_id) THEN
        RETURN FALSE;
    END IF;
    v_fee := ROUND(v_order.total_amount * 0.15,2);
    INSERT INTO order_escrows(order_id,chef_id,payment_transaction_id,gross_amount,platform_fee,chef_amount)
        VALUES (p_order_id,v_order.chef_id,p_payment_transaction_id,
                v_order.total_amount,v_fee,v_order.total_amount-v_fee)
        RETURNING id INTO v_escrow_id;
    INSERT INTO escrow_ledger_entries(order_id,escrow_id,entry_type,gross_amount,chef_amount,platform_fee,note)
        VALUES (p_order_id,v_escrow_id,'HELD',v_order.total_amount,
                v_order.total_amount-v_fee,v_fee,'Verified payment; 100% held');
    RETURN TRUE;
END $function$;
    $install_sql$;
    RAISE NOTICE 'Created function: public.gocook_register_verified_payment';
  ELSE
    RAISE NOTICE 'Already exists; unchanged: public.gocook_register_verified_payment';
  END IF;
END $install$;

-- Install gocook_accept_and_release only if absent; never replace modified implementation.
DO $install$
BEGIN
  IF to_regprocedure('public.gocook_accept_and_release(bigint,bigint)') IS NULL THEN
    EXECUTE $install_sql$
CREATE FUNCTION public.gocook_accept_and_release(
    p_order_id BIGINT, p_customer_id BIGINT
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $function$

DECLARE
    v_order orders%ROWTYPE;
    v_escrow order_escrows%ROWTYPE;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id=p_order_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Order % not found',p_order_id; END IF;
    IF v_order.customer_id IS DISTINCT FROM p_customer_id THEN
        RAISE EXCEPTION 'Customer is not order owner';
    END IF;
    IF v_order.status = 'COMPLETED' AND v_order.accepted_at IS NOT NULL THEN
        RETURN FALSE;
    END IF;
    IF v_order.status <> 'AWAITING_ACCEPTANCE' THEN
        RAISE EXCEPTION 'Order must await acceptance, actual: %', v_order.status;
    END IF;
    SELECT * INTO v_escrow FROM order_escrows WHERE order_id=p_order_id FOR UPDATE;
    IF NOT FOUND OR v_escrow.status <> 'HELD' THEN
        RAISE EXCEPTION 'No HELD escrow for order %',p_order_id;
    END IF;
    UPDATE orders SET status='COMPLETED',accepted_at=CURRENT_TIMESTAMP,
           updated_at=(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,
           version=version+1
     WHERE id=p_order_id;
    UPDATE order_escrows SET status='RELEASED',released_at=CURRENT_TIMESTAMP,
                             updated_at=CURRENT_TIMESTAMP WHERE id=v_escrow.id;
    INSERT INTO escrow_ledger_entries(order_id,escrow_id,entry_type,gross_amount,chef_amount,platform_fee,note)
        VALUES (p_order_id,v_escrow.id,'RELEASED',v_escrow.gross_amount,
                v_escrow.chef_amount,v_escrow.platform_fee,'Acceptance: simulated payout, no real bank transfer');
    INSERT INTO order_status_history(order_id,actor_user_id,previous_status,new_status,note)
        VALUES (p_order_id,p_customer_id,'AWAITING_ACCEPTANCE','COMPLETED','Customer accepted; simulated release');
    RETURN TRUE;
END $function$;
    $install_sql$;
    RAISE NOTICE 'Created function: public.gocook_accept_and_release';
  ELSE
    RAISE NOTICE 'Already exists; unchanged: public.gocook_accept_and_release';
  END IF;
END $install$;

-- Install gocook_chef_reject_order only if absent; never replace modified implementation.
DO $install$
BEGIN
  IF to_regprocedure('public.gocook_chef_reject_order(bigint,bigint,text)') IS NULL THEN
    EXECUTE $install_sql$
CREATE FUNCTION public.gocook_chef_reject_order(
    p_order_id BIGINT, p_chef_id BIGINT, p_reason TEXT
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $function$

DECLARE
    v_order orders%ROWTYPE;
    v_escrow order_escrows%ROWTYPE;
BEGIN
    SELECT * INTO v_order FROM orders WHERE id=p_order_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Order not found'; END IF;
    IF v_order.chef_id IS DISTINCT FROM p_chef_id THEN
        RAISE EXCEPTION 'Chef is not order owner';
    END IF;
    IF v_order.status = 'REJECTED' THEN RETURN FALSE; END IF;
    IF v_order.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Only PENDING orders can be rejected by Chef';
    END IF;
    SELECT * INTO v_escrow FROM order_escrows WHERE order_id=p_order_id FOR UPDATE;
    IF NOT FOUND OR v_escrow.status <> 'HELD' THEN
        RAISE EXCEPTION 'Expected HELD escrow';
    END IF;
    UPDATE orders SET status='REJECTED', rejected_reason=LEFT(COALESCE(p_reason,''),1000),
       updated_at=(EXTRACT(EPOCH FROM NOW()) * 1000)::bigint,version=version+1
     WHERE id=p_order_id;
    UPDATE order_escrows SET status='REFUND_PENDING',updated_at=CURRENT_TIMESTAMP
     WHERE id=v_escrow.id;
    UPDATE chef_bookings SET booking_status='RELEASED',updated_at=CURRENT_TIMESTAMP
     WHERE order_id=p_order_id;
    INSERT INTO escrow_ledger_entries(order_id,escrow_id,entry_type,gross_amount,chef_amount,platform_fee,note)
        VALUES (p_order_id,v_escrow.id,'REFUND_PENDING',v_escrow.gross_amount,
                v_escrow.chef_amount,v_escrow.platform_fee,'Chef rejected; payment team must refund');
    INSERT INTO order_status_history(order_id,actor_user_id,previous_status,new_status,note)
        VALUES (p_order_id,p_chef_id,'PENDING','REJECTED',p_reason);
    RETURN TRUE;
END $function$;
    $install_sql$;
    RAISE NOTICE 'Created function: public.gocook_chef_reject_order';
  ELSE
    RAISE NOTICE 'Already exists; unchanged: public.gocook_chef_reject_order';
  END IF;
END $install$;

-- Verification inside the same transaction (before COMMIT).
DO $verify$
BEGIN
  IF to_regprocedure('public.gocook_register_verified_payment(bigint,bigint)') IS NULL
     OR to_regprocedure('public.gocook_accept_and_release(bigint,bigint)') IS NULL
     OR to_regprocedure('public.gocook_chef_reject_order(bigint,bigint,text)') IS NULL THEN
    RAISE EXCEPTION 'At least one required function was not installed.';
  END IF;
END $verify$;

COMMIT;

-- No test invocation here: calling any of these functions on real orders
-- can change order status, escrow status, or ledger balances.
