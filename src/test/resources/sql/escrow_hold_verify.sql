-- P11: verifies that a verified payment is held 100% in escrow (15% platform / 85% chef) exactly once.
-- Run ONLY on a throwaway database that already has the baseline + chef migration applied, e.g.
--   docker exec -i gocook_tmdt_nhom2_2-postgres-1 psql -U app_user -d sprint1_verify -v ON_ERROR_STOP=1 -q < src/test/resources/sql/escrow_hold_verify.sql
-- Everything runs in one transaction that is rolled back at the end. Any failed check raises an exception.
BEGIN;

CREATE FUNCTION pg_temp.new_order(p_code text, p_chef bigint, p_customer bigint, p_amount numeric, p_status text)
RETURNS bigint LANGUAGE sql AS $$
    INSERT INTO orders(order_code, customer_name, product_name, guest_count, scheduled_at,
                       total_amount, status, customer_id, chef_id)
    VALUES (p_code, 'Test customer', 'Test menu', 4, now() + interval '3 days',
            p_amount, p_status, p_customer, p_chef)
    RETURNING id
$$;

CREATE FUNCTION pg_temp.new_payment(p_order bigint, p_amount numeric, p_status text, p_paid boolean)
RETURNS bigint LANGUAGE sql AS $$
    INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, paid_at)
    VALUES (p_order, 'VNPAY', 'T' || p_order || '-' || p_amount, p_amount, p_status,
            CASE WHEN p_paid THEN now() END)
    RETURNING id
$$;

CREATE FUNCTION pg_temp.expect_error(p_order bigint, p_payment bigint, p_fragment text, p_label text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        PERFORM gocook_register_verified_payment(p_order, p_payment);
    EXCEPTION WHEN OTHERS THEN
        IF position(p_fragment IN SQLERRM) = 0 THEN
            RAISE EXCEPTION 'FAIL %: unexpected error "%"', p_label, SQLERRM;
        END IF;
        IF EXISTS (SELECT 1 FROM order_escrows WHERE order_id = p_order) THEN
            RAISE EXCEPTION 'FAIL %: escrow was created despite the error', p_label;
        END IF;
        RAISE NOTICE 'PASS %: rejected with "%"', p_label, p_fragment;
        RETURN;
    END;
    RAISE EXCEPTION 'FAIL %: no error raised', p_label;
END $$;

DO $$
DECLARE
    v_chef bigint; v_customer bigint; v_order bigint; v_pay bigint;
    v_escrow order_escrows%ROWTYPE; v_count int;
BEGIN
    INSERT INTO users(username, password, email) VALUES ('p11chef', 'x', 'p11chef@x.test') RETURNING id INTO v_chef;
    INSERT INTO users(username, password, email) VALUES ('p11cust', 'x', 'p11cust@x.test') RETURNING id INTO v_customer;

    -- S1: success -> exactly one HELD escrow, 15% / 85%, one HELD ledger row
    v_order := pg_temp.new_order('P11-S1', v_chef, v_customer, 1000000, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 1000000, 'SUCCESS', true);
    IF NOT gocook_register_verified_payment(v_order, v_pay) THEN RAISE EXCEPTION 'FAIL S1: first call must return TRUE'; END IF;
    SELECT * INTO v_escrow FROM order_escrows WHERE order_id = v_order;
    IF v_escrow.status <> 'HELD' OR v_escrow.gross_amount <> 1000000
       OR v_escrow.platform_fee <> 150000 OR v_escrow.chef_amount <> 850000 THEN
        RAISE EXCEPTION 'FAIL S1: escrow is % gross=% fee=% chef=%', v_escrow.status, v_escrow.gross_amount, v_escrow.platform_fee, v_escrow.chef_amount;
    END IF;
    SELECT count(*) INTO v_count FROM escrow_ledger_entries WHERE order_id = v_order AND entry_type = 'HELD';
    IF v_count <> 1 THEN RAISE EXCEPTION 'FAIL S1: % HELD ledger rows', v_count; END IF;
    IF (SELECT status FROM orders WHERE id = v_order) <> 'PENDING' THEN RAISE EXCEPTION 'FAIL S1: order must stay PENDING'; END IF;
    RAISE NOTICE 'PASS S1: one HELD escrow 15/85, one ledger row, order stays PENDING';

    -- S2: repeated callback -> FALSE, still one escrow and one ledger row
    IF gocook_register_verified_payment(v_order, v_pay) THEN RAISE EXCEPTION 'FAIL S2: replay must return FALSE'; END IF;
    IF gocook_register_verified_payment(v_order, v_pay) THEN RAISE EXCEPTION 'FAIL S2: second replay must return FALSE'; END IF;
    SELECT count(*) INTO v_count FROM order_escrows WHERE order_id = v_order;
    IF v_count <> 1 THEN RAISE EXCEPTION 'FAIL S2: % escrows', v_count; END IF;
    SELECT count(*) INTO v_count FROM escrow_ledger_entries WHERE order_id = v_order;
    IF v_count <> 1 THEN RAISE EXCEPTION 'FAIL S2: % ledger rows', v_count; END IF;
    RAISE NOTICE 'PASS S2: replays return FALSE, no extra escrow or ledger row';

    -- S3: odd amount keeps gross = fee + chef (rounding goes to the fee)
    v_order := pg_temp.new_order('P11-S3', v_chef, v_customer, 333333, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 333333, 'SUCCESS', true);
    PERFORM gocook_register_verified_payment(v_order, v_pay);
    SELECT * INTO v_escrow FROM order_escrows WHERE order_id = v_order;
    IF v_escrow.gross_amount <> v_escrow.platform_fee + v_escrow.chef_amount OR v_escrow.platform_fee <> 49999.95 THEN
        RAISE EXCEPTION 'FAIL S3: gross=% fee=% chef=%', v_escrow.gross_amount, v_escrow.platform_fee, v_escrow.chef_amount;
    END IF;
    RAISE NOTICE 'PASS S3: odd amount splits exactly (fee %, chef %)', v_escrow.platform_fee, v_escrow.chef_amount;

    -- S4: order without chef -> rejected, nothing held
    v_order := pg_temp.new_order('P11-S4', NULL, v_customer, 500000, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 500000, 'SUCCESS', true);
    PERFORM pg_temp.expect_error(v_order, v_pay, 'Assign chef_id', 'S4 missing chef');

    -- S5: payment amount differs from order total -> rejected
    v_order := pg_temp.new_order('P11-S5', v_chef, v_customer, 500000, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 400000, 'SUCCESS', true);
    PERFORM pg_temp.expect_error(v_order, v_pay, 'Payment mismatch', 'S5 amount mismatch');

    -- S6: order already moved past PENDING -> rejected
    v_order := pg_temp.new_order('P11-S6', v_chef, v_customer, 500000, 'CONFIRMED');
    v_pay := pg_temp.new_payment(v_order, 500000, 'SUCCESS', true);
    PERFORM pg_temp.expect_error(v_order, v_pay, 'must be PENDING', 'S6 order not pending');

    -- S7: payment not SUCCESS -> rejected
    v_order := pg_temp.new_order('P11-S7', v_chef, v_customer, 500000, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 500000, 'PENDING', false);
    PERFORM pg_temp.expect_error(v_order, v_pay, 'Missing verified', 'S7 payment pending');

    -- S8: SUCCESS payment without paid_at -> rejected
    v_order := pg_temp.new_order('P11-S8', v_chef, v_customer, 500000, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 500000, 'SUCCESS', false);
    PERFORM pg_temp.expect_error(v_order, v_pay, 'Missing verified', 'S8 no paid_at');

    -- S9: payment of another order -> rejected
    v_order := pg_temp.new_order('P11-S9', v_chef, v_customer, 500000, 'PENDING');
    PERFORM pg_temp.expect_error(v_order, pg_temp.new_payment(pg_temp.new_order('P11-S9b', v_chef, v_customer, 500000, 'PENDING'), 500000, 'SUCCESS', true), 'Missing verified', 'S9 foreign payment');

    -- S10: the tables themselves refuse a second escrow or a second HELD ledger row
    v_order := pg_temp.new_order('P11-S10', v_chef, v_customer, 100000, 'PENDING');
    v_pay := pg_temp.new_payment(v_order, 100000, 'SUCCESS', true);
    PERFORM gocook_register_verified_payment(v_order, v_pay);
    BEGIN
        INSERT INTO order_escrows(order_id, chef_id, payment_transaction_id, gross_amount, platform_fee, chef_amount)
        VALUES (v_order, v_chef, v_pay, 100000, 15000, 85000);
        RAISE EXCEPTION 'FAIL S10: second escrow was accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS S10a: second escrow refused by UNIQUE(order_id)';
    END;
    BEGIN
        INSERT INTO escrow_ledger_entries(order_id, escrow_id, entry_type, gross_amount, chef_amount, platform_fee)
        SELECT v_order, id, 'HELD', 100000, 85000, 15000 FROM order_escrows WHERE order_id = v_order;
        RAISE EXCEPTION 'FAIL S10: second HELD ledger row was accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS S10b: second HELD ledger row refused by UNIQUE(order_id, entry_type)';
    END;

    -- Monitoring query: SUCCESS payments that never got an escrow (S4, S5, S6, S8 and S9b leftovers show up here)
    SELECT count(*) INTO v_count
      FROM payment_transactions p LEFT JOIN order_escrows e ON e.order_id = p.order_id
     WHERE p.status = 'SUCCESS' AND e.id IS NULL;
    RAISE NOTICE 'INFO: monitoring query finds % SUCCESS-without-escrow rows (expected 5: S4, S5, S6, S8, S9b)', v_count;
    IF v_count <> 5 THEN RAISE EXCEPTION 'FAIL monitoring: expected 5 rows, got %', v_count; END IF;
END $$;

ROLLBACK;
