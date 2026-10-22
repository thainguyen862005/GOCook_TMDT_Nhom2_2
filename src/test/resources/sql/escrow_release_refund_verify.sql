-- P12/P13 on a THROWAWAY database: escrow HELD -> release, HELD -> refund, REFUND_PENDING -> refunded, every step twice.
-- Needs the chain up to sql/migrations/refunds_20261022.sql applied.
-- The refund statements are the ones RefundStore runs (refund.RefundStore); the release uses the real function.
-- Run: docker compose exec -T postgres psql -U app_user -d sprint2b_verify -v ON_ERROR_STOP=1 -q -f /tmp/escrow_release_refund_verify.sql
-- One transaction, rolled back at the end. Any failed check raises an exception.
BEGIN;

CREATE FUNCTION pg_temp.paid_order(p_code text, p_chef bigint, p_customer bigint, p_status text)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_order bigint; v_pay bigint;
BEGIN
    INSERT INTO orders(order_code, customer_name, product_name, guest_count, scheduled_at, total_amount, status, customer_id, chef_id)
    VALUES (p_code, 'T', 'M', 4, now() + interval '3 days', 1000000, 'PENDING', p_customer, p_chef) RETURNING id INTO v_order;
    INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, attempt_no, expires_at, paid_at)
    VALUES (v_order, 'VNPAY', p_code, 1000000, 'SUCCESS', 1, now(), now()) RETURNING id INTO v_pay;
    PERFORM gocook_register_verified_payment(v_order, v_pay);
    IF p_status <> 'PENDING' THEN UPDATE orders SET status = p_status WHERE id = v_order; END IF;
    RETURN v_order;
END $$;

-- The RefundStore statements, in the order RefundService runs them.
CREATE FUNCTION pg_temp.refund_held(p_order bigint, p_amount numeric, p_reason text)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_escrow order_escrows%ROWTYPE; v_pay bigint; v_refund bigint;
BEGIN
    PERFORM 1 FROM orders WHERE id = p_order FOR UPDATE;
    SELECT * INTO v_escrow FROM order_escrows WHERE order_id = p_order FOR UPDATE;
    SELECT id INTO v_pay FROM payment_transactions WHERE id = v_escrow.payment_transaction_id FOR UPDATE;
    SELECT id INTO v_refund FROM refunds WHERE payment_transaction_id = v_pay ORDER BY id DESC LIMIT 1;
    IF v_refund IS NOT NULL THEN RETURN v_refund; END IF;           -- second call: the existing refund
    IF v_escrow.status = 'RELEASED' THEN RAISE EXCEPTION 'escrow already released'; END IF;
    INSERT INTO refunds(order_id, payment_transaction_id, amount, retained_amount, policy_percent, reason_code, status)
    VALUES (p_order, v_pay, p_amount, v_escrow.gross_amount - p_amount, (p_amount * 100 / v_escrow.gross_amount)::int, p_reason, 'REQUESTED')
    RETURNING id INTO v_refund;
    IF v_escrow.status = 'HELD' THEN
        UPDATE order_escrows SET status = 'REFUND_PENDING', updated_at = now() WHERE id = v_escrow.id;
        INSERT INTO escrow_ledger_entries(order_id, escrow_id, entry_type, gross_amount, chef_amount, platform_fee, note)
        VALUES (p_order, v_escrow.id, 'REFUND_PENDING', v_escrow.gross_amount, v_escrow.chef_amount, v_escrow.platform_fee, 'refund')
        ON CONFLICT (order_id, entry_type) DO NOTHING;
    END IF;
    UPDATE refunds SET status = 'REFUNDED', gateway_reference = 'SIM-' || v_refund, updated_at = now() WHERE id = v_refund;
    UPDATE order_escrows SET status = 'REFUNDED', updated_at = now() WHERE id = v_escrow.id;
    INSERT INTO escrow_ledger_entries(order_id, escrow_id, entry_type, gross_amount, chef_amount, platform_fee, note)
    VALUES (p_order, v_escrow.id, 'REFUNDED', p_amount, 0, 0, 'refunded')
    ON CONFLICT (order_id, entry_type) DO NOTHING;
    IF p_amount = v_escrow.gross_amount THEN
        UPDATE payment_transactions SET status = 'REFUNDED', version = version + 1 WHERE id = v_pay;
    END IF;
    RETURN v_refund;
END $$;

DO $$
DECLARE
    v_chef bigint; v_cust bigint; v_order bigint; v_refund bigint; v_again bigint; v_count int; v_status text;
BEGIN
    INSERT INTO users(username, password, email) VALUES ('p13chef', 'x', 'p13chef@x.test') RETURNING id INTO v_chef;
    INSERT INTO users(username, password, email) VALUES ('p13cust', 'x', 'p13cust@x.test') RETURNING id INTO v_cust;

    -- 1. HELD -> release, called twice
    v_order := pg_temp.paid_order('P13-REL', v_chef, v_cust, 'AWAITING_ACCEPTANCE');
    IF NOT gocook_accept_and_release(v_order, v_cust) THEN RAISE EXCEPTION 'FAIL 1: first release must return TRUE'; END IF;
    IF gocook_accept_and_release(v_order, v_cust) THEN RAISE EXCEPTION 'FAIL 1: second release must return FALSE'; END IF;
    SELECT count(*) INTO v_count FROM escrow_ledger_entries WHERE order_id = v_order AND entry_type = 'RELEASED';
    IF v_count <> 1 THEN RAISE EXCEPTION 'FAIL 1: % RELEASED ledger rows', v_count; END IF;
    IF (SELECT status FROM order_escrows WHERE order_id = v_order) <> 'RELEASED' THEN RAISE EXCEPTION 'FAIL 1: escrow not RELEASED'; END IF;
    RAISE NOTICE 'PASS 1: HELD -> RELEASED once, second call returns FALSE, one ledger row';

    -- 1b. a released escrow cannot be refunded
    BEGIN
        PERFORM pg_temp.refund_held(v_order, 1000000, 'CUSTOMER_CANCEL');
        RAISE EXCEPTION 'FAIL 1b: refund after release was accepted';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'escrow already released' THEN RAISE; END IF;
        RAISE NOTICE 'PASS 1b: refund after release refused';
    END;

    -- 2. HELD -> refund (full), called twice
    v_order := pg_temp.paid_order('P13-FULL', v_chef, v_cust, 'CANCELLED');
    v_refund := pg_temp.refund_held(v_order, 1000000, 'CUSTOMER_CANCEL');
    v_again := pg_temp.refund_held(v_order, 1000000, 'CUSTOMER_CANCEL');
    IF v_refund <> v_again THEN RAISE EXCEPTION 'FAIL 2: second call made another refund'; END IF;
    SELECT status INTO v_status FROM order_escrows WHERE order_id = v_order;
    IF v_status <> 'REFUNDED' THEN RAISE EXCEPTION 'FAIL 2: escrow is %', v_status; END IF;
    IF (SELECT status FROM payment_transactions WHERE order_id = v_order) <> 'REFUNDED' THEN RAISE EXCEPTION 'FAIL 2: payment not REFUNDED'; END IF;
    SELECT count(*) INTO v_count FROM escrow_ledger_entries WHERE order_id = v_order AND entry_type IN ('REFUND_PENDING', 'REFUNDED');
    IF v_count <> 2 THEN RAISE EXCEPTION 'FAIL 2: % refund ledger rows, expected 2', v_count; END IF;
    BEGIN
        INSERT INTO refunds(order_id, payment_transaction_id, amount, retained_amount, policy_percent, reason_code, status)
        SELECT v_order, id, 1, 0, 1, 'CUSTOMER_CANCEL', 'REQUESTED' FROM payment_transactions WHERE order_id = v_order;
        RAISE EXCEPTION 'FAIL 2: a second active refund for one payment was accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS 2: HELD -> REFUND_PENDING -> REFUNDED, repeat harmless, 2nd active refund refused by the index';
    END;

    -- 3. REFUND_PENDING (chef reject function) -> refunded, called twice
    v_order := pg_temp.paid_order('P13-REJ', v_chef, v_cust, 'PENDING');
    IF NOT gocook_chef_reject_order(v_order, v_chef, 'busy') THEN RAISE EXCEPTION 'FAIL 3: reject must return TRUE'; END IF;
    IF gocook_chef_reject_order(v_order, v_chef, 'busy') THEN RAISE EXCEPTION 'FAIL 3: second reject must return FALSE'; END IF;
    IF (SELECT status FROM order_escrows WHERE order_id = v_order) <> 'REFUND_PENDING' THEN RAISE EXCEPTION 'FAIL 3: escrow is not REFUND_PENDING'; END IF;
    v_refund := pg_temp.refund_held(v_order, 1000000, 'CHEF_REJECTED');
    v_again := pg_temp.refund_held(v_order, 1000000, 'CHEF_REJECTED');
    IF v_refund <> v_again THEN RAISE EXCEPTION 'FAIL 3: second call made another refund'; END IF;
    IF (SELECT status FROM order_escrows WHERE order_id = v_order) <> 'REFUNDED' THEN RAISE EXCEPTION 'FAIL 3: escrow not REFUNDED'; END IF;
    SELECT count(*) INTO v_count FROM escrow_ledger_entries WHERE order_id = v_order AND entry_type = 'REFUND_PENDING';
    IF v_count <> 1 THEN RAISE EXCEPTION 'FAIL 3: % REFUND_PENDING rows (the function wrote one, the refund must not add another)', v_count; END IF;
    RAISE NOTICE 'PASS 3: REFUND_PENDING -> REFUNDED, repeat harmless, one REFUND_PENDING ledger row';

    -- 4. partial refund keeps the payment SUCCESS
    v_order := pg_temp.paid_order('P13-HALF', v_chef, v_cust, 'CANCELLED');
    v_refund := pg_temp.refund_held(v_order, 500000, 'CUSTOMER_CANCEL');
    IF (SELECT status FROM payment_transactions WHERE order_id = v_order) <> 'SUCCESS' THEN RAISE EXCEPTION 'FAIL 4: partial refund changed the payment'; END IF;
    IF (SELECT retained_amount FROM refunds WHERE id = v_refund) <> 500000 THEN RAISE EXCEPTION 'FAIL 4: retained not recorded'; END IF;
    RAISE NOTICE 'PASS 4: partial refund, payment stays SUCCESS, retained 500000 recorded';

    -- 5. views count HELD/RELEASED only (refunded money is not the chef's)
    SELECT held_amount INTO v_count FROM v_chef_financial_summary WHERE chef_id = v_chef;
    IF v_count IS NOT NULL AND v_count <> 0 THEN RAISE EXCEPTION 'FAIL 5: view still counts % as held', v_count; END IF;
    RAISE NOTICE 'PASS 5: v_chef_financial_summary ignores REFUNDED/REFUND_PENDING escrows';
END $$;

ROLLBACK;
