-- Sprint 2 part 1 checks on a THROWAWAY database: payment attempts (P10) and voucher counters (P09).
-- Needs: go_cook_base, admin_management, chef_migration, payment_migration, sql/a01/04, payment_idempotency,
--        payment_attempts_20261022 and voucher_redemptions_20261022 applied.
-- Run: docker compose exec -T postgres psql -U app_user -d sprint2a_verify -v ON_ERROR_STOP=1 -q -f /tmp/sprint2a_verify.sql
-- One transaction, rolled back at the end. Any failed check raises an exception.
BEGIN;

CREATE FUNCTION pg_temp.new_order(p_code text, p_chef bigint, p_customer bigint, p_amount numeric)
RETURNS bigint LANGUAGE sql AS $$
    INSERT INTO orders(order_code, customer_name, product_name, guest_count, scheduled_at,
                       total_amount, status, customer_id, chef_id)
    VALUES (p_code, 'Test customer', 'Test menu', 4, now() + interval '3 days',
            p_amount, 'PENDING', p_customer, p_chef)
    RETURNING id
$$;

CREATE FUNCTION pg_temp.new_attempt(p_order bigint, p_no int, p_status text, p_code text)
RETURNS bigint LANGUAGE sql AS $$
    INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, attempt_no, expires_at, paid_at)
    VALUES (p_order, 'VNPAY', p_code, 1000000, p_status, p_no, now() + interval '15 minutes',
            CASE WHEN p_status = 'SUCCESS' THEN now() END)
    RETURNING id
$$;

DO $$
DECLARE
    v_chef bigint; v_c1 bigint; v_c2 bigint;
    v_o1 bigint; v_o2 bigint; v_o3 bigint; v_o4 bigint;
    v_p1 bigint; v_p2 bigint; v_voucher bigint;
    v_count int; v_used int; v_expected timestamptz; v_actual timestamptz;
BEGIN
    INSERT INTO users(username, password, email) VALUES ('s2chef', 'x', 's2chef@x.test') RETURNING id INTO v_chef;
    INSERT INTO users(username, password, email) VALUES ('s2c1', 'x', 's2c1@x.test') RETURNING id INTO v_c1;
    INSERT INTO users(username, password, email) VALUES ('s2c2', 'x', 's2c2@x.test') RETURNING id INTO v_c2;

    -- ===== P10 attempts =====
    v_o1 := pg_temp.new_order('S2-A1', v_chef, v_c1, 1000000);
    PERFORM pg_temp.new_attempt(v_o1, 1, 'CANCELLED', 'S2-A1');
    UPDATE payment_transactions SET close_reason = 'USER_CANCELLED' WHERE order_id = v_o1 AND attempt_no = 1;
    PERFORM pg_temp.new_attempt(v_o1, 2, 'PENDING', 'S2-A1A2');
    SELECT count(*) INTO v_count FROM payment_transactions WHERE order_id = v_o1;
    IF v_count <> 2 THEN RAISE EXCEPTION 'FAIL A1: expected 2 attempts, got %', v_count; END IF;
    RAISE NOTICE 'PASS A1: one order holds attempt 1 (txnRef = order code) and attempt 2 (order code + A2)';

    BEGIN
        PERFORM pg_temp.new_attempt(v_o1, 2, 'PENDING', 'S2-A1A2B');
        RAISE EXCEPTION 'FAIL A2: second attempt number 2 was accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS A2: unique (order_id, attempt_no) refuses a duplicate attempt number';
    END;

    BEGIN
        PERFORM pg_temp.new_attempt(pg_temp.new_order('S2-A1X', v_chef, v_c1, 1000000), 1, 'PENDING', 'S2-A1A2');
        RAISE EXCEPTION 'FAIL A3: a txnRef was reused by another order';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS A3: unique (provider, transaction_code) keeps every txnRef unique';
    END;

    -- Backfill expression used by payment_attempts_20261022.sql (wall time Asia/Ho_Chi_Minh).
    v_o4 := pg_temp.new_order('S2-L1', v_chef, v_c1, 1000);
    INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, raw_reference)
    VALUES (v_o4, 'VNPAY', 'S2-LEGACY', 1000, 'PENDING', '{"createDate":"20261021203600","expireDate":"20261021205100"}');
    UPDATE payment_transactions
       SET expires_at = (to_timestamp(substring(raw_reference FROM '"expireDate":"([0-9]{14})"'), 'YYYYMMDDHH24MISS')
                           AT TIME ZONE current_setting('TimeZone')) AT TIME ZONE 'Asia/Ho_Chi_Minh'
     WHERE transaction_code = 'S2-LEGACY' AND expires_at IS NULL;
    SELECT expires_at INTO v_actual FROM payment_transactions WHERE transaction_code = 'S2-LEGACY';
    v_expected := '2026-10-21 20:51:00+07'::timestamptz;
    IF v_actual <> v_expected THEN RAISE EXCEPTION 'FAIL A4: backfill gave %, expected %', v_actual, v_expected; END IF;
    RAISE NOTICE 'PASS A4: backfill turns expireDate 20261021205100 into % ', v_actual;
    DELETE FROM payment_transactions WHERE transaction_code = 'S2-LEGACY';

    -- Duplicate payment: two SUCCESS attempts on one order; only the first is held in escrow.
    v_o2 := pg_temp.new_order('S2-D1', v_chef, v_c1, 1000000);
    v_p1 := pg_temp.new_attempt(v_o2, 1, 'SUCCESS', 'S2-D1');
    v_p2 := pg_temp.new_attempt(v_o2, 2, 'SUCCESS', 'S2-D1A2');
    IF NOT gocook_register_verified_payment(v_o2, v_p1) THEN RAISE EXCEPTION 'FAIL D1: first payment must settle'; END IF;
    IF (SELECT payment_transaction_id FROM order_escrows WHERE order_id = v_o2) <> v_p1 THEN
        RAISE EXCEPTION 'FAIL D1: escrow holds the wrong payment';
    END IF;
    -- findSuccessWithoutEscrow
    SELECT count(*) INTO v_count FROM payment_transactions p
     WHERE p.status = 'SUCCESS'
       AND NOT EXISTS (SELECT 1 FROM order_escrows e WHERE e.payment_transaction_id = p.id)
       AND p.order_id = v_o2;
    IF v_count <> 1 THEN RAISE EXCEPTION 'FAIL D2: findSuccessWithoutEscrow found % rows for the order, expected 1', v_count; END IF;
    IF NOT EXISTS (SELECT 1 FROM payment_transactions p WHERE p.id = v_p2 AND p.status = 'SUCCESS'
                      AND NOT EXISTS (SELECT 1 FROM order_escrows e WHERE e.payment_transaction_id = p.id)) THEN
        RAISE EXCEPTION 'FAIL D2: the duplicate payment is not the one reported';
    END IF;
    RAISE NOTICE 'PASS D1/D2: escrow holds attempt 1; the duplicate attempt 2 is the only SUCCESS without escrow';

    -- ===== P09 voucher counters =====
    INSERT INTO admin_vouchers(code, discount_type, discount_value, min_order_amount, usage_limit, starts_at, ends_at, is_active)
    VALUES ('S2VOUCHER', 'PERCENT', 10, 0, 2, now() - interval '1 day', now() + interval '1 day', true)
    RETURNING id INTO v_voucher;
    v_o3 := pg_temp.new_order('S2-V1', v_chef, v_c1, 900000);

    -- reserve (same statements as VoucherService.reserve)
    UPDATE admin_vouchers SET used_count = used_count + 1 WHERE id = v_voucher;
    INSERT INTO voucher_redemptions(voucher_id, order_id, customer_id, discount_amount, status)
    VALUES (v_voucher, v_o3, v_c1, 100000, 'RESERVED');
    SELECT used_count INTO v_used FROM admin_vouchers WHERE id = v_voucher;
    IF v_used <> 1 THEN RAISE EXCEPTION 'FAIL V1: used_count % after reserve', v_used; END IF;
    RAISE NOTICE 'PASS V1: reserve counts one use';

    BEGIN
        INSERT INTO voucher_redemptions(voucher_id, order_id, customer_id, discount_amount, status)
        VALUES (v_voucher, v_o3, v_c1, 1, 'RESERVED');
        RAISE EXCEPTION 'FAIL V2: a second redemption for the same order was accepted';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS V2: one redemption per order (UNIQUE order_id)';
    END;

    BEGIN
        INSERT INTO voucher_redemptions(voucher_id, order_id, customer_id, discount_amount, status)
        VALUES (v_voucher, v_o2, v_c1, 1, 'BOGUS');
        RAISE EXCEPTION 'FAIL V3: a bogus status was accepted';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'PASS V3: status CHECK refuses unknown values';
    END;

    -- consume (twice) keeps the count
    UPDATE voucher_redemptions SET status = 'CONSUMED', updated_at = now() WHERE order_id = v_o3 AND status = 'RESERVED';
    UPDATE voucher_redemptions SET status = 'CONSUMED', updated_at = now() WHERE order_id = v_o3 AND status = 'RESERVED';
    SELECT used_count INTO v_used FROM admin_vouchers WHERE id = v_voucher;
    IF v_used <> 1 OR (SELECT status FROM voucher_redemptions WHERE order_id = v_o3) <> 'CONSUMED' THEN
        RAISE EXCEPTION 'FAIL V4: consume changed the counter or did not consume';
    END IF;
    -- a consumed redemption is NOT released
    WITH released AS (
      UPDATE voucher_redemptions SET status = 'RELEASED', updated_at = now()
      WHERE order_id = v_o3 AND status = 'RESERVED' RETURNING voucher_id)
    UPDATE admin_vouchers v SET used_count = GREATEST(v.used_count - 1, 0) FROM released WHERE v.id = released.voucher_id;
    SELECT used_count INTO v_used FROM admin_vouchers WHERE id = v_voucher;
    IF v_used <> 1 THEN RAISE EXCEPTION 'FAIL V4: release touched a consumed redemption (used_count %)', v_used; END IF;
    RAISE NOTICE 'PASS V4: consume is idempotent and a consumed use is never released';

    -- release path on a fresh reservation, executed twice
    v_o3 := pg_temp.new_order('S2-V2', v_chef, v_c2, 900000);
    UPDATE admin_vouchers SET used_count = used_count + 1 WHERE id = v_voucher;
    INSERT INTO voucher_redemptions(voucher_id, order_id, customer_id, discount_amount, status)
    VALUES (v_voucher, v_o3, v_c2, 90000, 'RESERVED');
    FOR i IN 1..2 LOOP
        WITH released AS (
          UPDATE voucher_redemptions SET status = 'RELEASED', updated_at = now()
          WHERE order_id = v_o3 AND status = 'RESERVED' RETURNING voucher_id)
        UPDATE admin_vouchers v SET used_count = GREATEST(v.used_count - 1, 0) FROM released WHERE v.id = released.voucher_id;
    END LOOP;
    SELECT used_count INTO v_used FROM admin_vouchers WHERE id = v_voucher;
    IF v_used <> 1 THEN RAISE EXCEPTION 'FAIL V5: used_count % after releasing twice, expected 1', v_used; END IF;
    RAISE NOTICE 'PASS V5: releasing twice gives the use back once';

    -- never below zero
    UPDATE admin_vouchers SET used_count = 0 WHERE id = v_voucher;
    UPDATE admin_vouchers SET used_count = GREATEST(used_count - 1, 0) WHERE id = v_voucher;
    SELECT used_count INTO v_used FROM admin_vouchers WHERE id = v_voucher;
    IF v_used <> 0 THEN RAISE EXCEPTION 'FAIL V6: used_count went to %', v_used; END IF;
    RAISE NOTICE 'PASS V6: used_count never goes below zero';

    -- per-customer count (RESERVED or CONSUMED only)
    SELECT count(*) INTO v_count FROM voucher_redemptions
     WHERE voucher_id = v_voucher AND customer_id = v_c2 AND status IN ('RESERVED', 'CONSUMED');
    IF v_count <> 0 THEN RAISE EXCEPTION 'FAIL V7: released redemption still counts for the customer'; END IF;
    RAISE NOTICE 'PASS V7: a released redemption does not count against the customer limit';
END $$;

ROLLBACK;
