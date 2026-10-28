package com.james.LMS.voucher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Voucher counters against a real PostgreSQL (row locks included). Runs only when
 * GOCOOK_VOUCHER_TEST_URL points to a throwaway database named like sprint2a_verify; see
 * docs/handoff-sprint2a.md.
 */
@DataJpaTest(
    properties = {
      "spring.datasource.url=${GOCOOK_VOUCHER_TEST_URL}",
      "spring.datasource.username=app_user",
      "spring.datasource.password=${GOCOOK_VOUCHER_TEST_PASSWORD:app_password}",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.show-sql=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GOCOOK_VOUCHER_TEST_URL", matches = ".+sprint2a_verify.*")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoucherPersistenceTest {
  static final BigDecimal SUBTOTAL = new BigDecimal("500000");

  @Autowired NamedParameterJdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;

  VoucherService service;
  TransactionTemplate transactions;
  String code;
  Long voucherId;
  Long chef;
  Long customerA;
  Long customerB;

  @BeforeEach
  void fixtures() {
    service = new VoucherService(jdbc, new VoucherProperties());
    transactions = new TransactionTemplate(transactionManager);
    String tag = UUID.randomUUID().toString().substring(0, 8);
    code = "T" + tag.toUpperCase();
    chef = user("chef" + tag);
    customerA = user("a" + tag);
    customerB = user("b" + tag);
  }

  @AfterEach
  void cleanUp() {
    MapSqlParameterSource byTag = new MapSqlParameterSource("code", code);
    jdbc.update(
        "DELETE FROM voucher_redemptions WHERE voucher_id IN (SELECT id FROM admin_vouchers WHERE code = :code)",
        byTag);
    jdbc.update("DELETE FROM orders WHERE order_code LIKE :prefix", new MapSqlParameterSource("prefix", code + "%"));
    jdbc.update("DELETE FROM admin_vouchers WHERE code = :code", byTag);
    for (Long id : new Long[] {chef, customerA, customerB}) {
      jdbc.update("DELETE FROM users WHERE id = :id", new MapSqlParameterSource("id", id));
    }
  }

  Long user(String name) {
    return jdbc.queryForObject(
        "INSERT INTO users(username, password, email) VALUES (:n, 'x', :e) RETURNING id",
        new MapSqlParameterSource().addValue("n", name).addValue("e", name + "@example.invalid"),
        Long.class);
  }

  void voucher(int usageLimit) {
    voucherId =
        jdbc.queryForObject(
            """
            INSERT INTO admin_vouchers(code, discount_type, discount_value, min_order_amount, usage_limit,
                                       starts_at, ends_at, is_active)
            VALUES (:code, 'PERCENT', 10, 0, :limit, now() - interval '1 day', now() + interval '1 day', true)
            RETURNING id
            """,
            new MapSqlParameterSource().addValue("code", code).addValue("limit", usageLimit),
            Long.class);
  }

  Long order(String suffix, Long customer) {
    return jdbc.queryForObject(
        """
        INSERT INTO orders(order_code, customer_name, product_name, guest_count, scheduled_at,
                           total_amount, status, customer_id, chef_id)
        VALUES (:code, 'Test', 'Menu', 4, now() + interval '3 days', 450000, 'PENDING', :customer, :chef)
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("code", code + suffix)
            .addValue("customer", customer)
            .addValue("chef", chef),
        Long.class);
  }

  int usedCount() {
    return jdbc.queryForObject(
        "SELECT used_count FROM admin_vouchers WHERE id = :id",
        new MapSqlParameterSource("id", voucherId),
        Integer.class);
  }

  String redemptionStatus(Long orderId) {
    return jdbc.queryForObject(
        "SELECT status FROM voucher_redemptions WHERE order_id = :id",
        new MapSqlParameterSource("id", orderId),
        String.class);
  }

  void reserve(Long orderId, Long customer) {
    OffsetDateTime now = OffsetDateTime.now();
    transactions.executeWithoutResult(
        status -> {
          VoucherQuote quote = service.validateAndCalculate(code, customer, SUBTOTAL, now);
          service.reserve(orderId, customer, SUBTOTAL, quote, now);
        });
  }

  @Test
  void twoCheckoutsRacingForTheLastCouponExactlyOneWins() throws Exception {
    voucher(1);
    Long orderA = order("A", customerA);
    Long orderB = order("B", customerB);
    CyclicBarrier bothValidated = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Throwable>> results = new ArrayList<>();
      for (Long[] pair : new Long[][] {{orderA, customerA}, {orderB, customerB}}) {
        Callable<Throwable> checkout =
            () -> {
              OffsetDateTime now = OffsetDateTime.now();
              try {
                transactions.executeWithoutResult(
                    status -> {
                      VoucherQuote quote = service.validateAndCalculate(code, pair[1], SUBTOTAL, now);
                      await(bothValidated);
                      service.reserve(pair[0], pair[1], SUBTOTAL, quote, now);
                    });
                return null;
              } catch (Throwable failure) {
                return failure;
              }
            };
        results.add(pool.submit(checkout));
      }
      List<Throwable> failures = new ArrayList<>();
      for (Future<Throwable> result : results) {
        Throwable failure = result.get();
        if (failure != null) {
          failures.add(failure);
        }
      }

      assertThat(failures).hasSize(1);
      assertThat(failures.get(0))
          .isInstanceOfSatisfying(
              PaymentException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VOUCHER_EXHAUSTED));
      assertThat(usedCount()).isEqualTo(1);
      Integer reserved =
          jdbc.queryForObject(
              "SELECT count(*) FROM voucher_redemptions WHERE voucher_id = :id AND status = 'RESERVED'",
              new MapSqlParameterSource("id", voucherId),
              Integer.class);
      assertThat(reserved).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void reserveConsumeAndReleaseMoveTheCounterOnce() {
    voucher(5);
    Long kept = order("K", customerA);
    Long dropped = order("D", customerB);

    reserve(kept, customerA);
    reserve(dropped, customerB);
    assertThat(usedCount()).isEqualTo(2);

    service.consume(kept);
    service.consume(kept);
    service.release(dropped);
    service.release(dropped);
    service.release(kept);

    assertThat(usedCount()).isEqualTo(1);
    assertThat(redemptionStatus(kept)).isEqualTo("CONSUMED");
    assertThat(redemptionStatus(dropped)).isEqualTo("RELEASED");
  }

  @Test
  void aCustomerCannotHoldASecondRedemptionButCanAfterReleasingTheFirst() {
    voucher(5);
    Long first = order("F", customerA);
    Long second = order("S", customerA);
    reserve(first, customerA);

    assertThatThrownBy(() -> reserve(second, customerA))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VOUCHER_CUSTOMER_LIMIT));
    assertThat(usedCount()).isEqualTo(1);

    service.release(first);
    reserve(second, customerA);

    assertThat(usedCount()).isEqualTo(1);
    assertThat(redemptionStatus(second)).isEqualTo("RESERVED");
  }

  @Test
  void aFailedCheckoutRollsTheReservationBack() {
    voucher(5);
    Long orderA = order("R", customerA);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      OffsetDateTime now = OffsetDateTime.now();
                      VoucherQuote quote = service.validateAndCalculate(code, customerA, SUBTOTAL, now);
                      service.reserve(orderA, customerA, SUBTOTAL, quote, now);
                      throw new IllegalStateException("payment failed after the voucher was reserved");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(usedCount()).isZero();
    Integer rows =
        jdbc.queryForObject(
            "SELECT count(*) FROM voucher_redemptions WHERE order_id = :id",
            new MapSqlParameterSource("id", orderA),
            Integer.class);
    assertThat(rows).isZero();
  }
}
