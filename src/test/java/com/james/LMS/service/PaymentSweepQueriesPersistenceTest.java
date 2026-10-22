package com.james.LMS.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.PendingOrderRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two queries the expiry sweeper relies on, against a real PostgreSQL. Runs only when
 * GOCOOK_SWEEP_TEST_URL points to a throwaway database named like sprint2d_verify.
 */
@DataJpaTest(
    properties = {
      "spring.datasource.url=${GOCOOK_SWEEP_TEST_URL}",
      "spring.datasource.username=app_user",
      "spring.datasource.password=${GOCOOK_SWEEP_TEST_PASSWORD:app_password}",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.show-sql=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GOCOOK_SWEEP_TEST_URL", matches = ".+sprint2[a-z]_verify.*")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentSweepQueriesPersistenceTest {

  @Autowired NamedParameterJdbcTemplate jdbc;
  @Autowired PaymentTransactionRepository payments;
  @Autowired PendingOrderRepository pendingOrders;

  String tag;
  Long chef;

  @BeforeEach
  void fixtures() {
    tag = "SW" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    chef =
        jdbc.queryForObject(
            "INSERT INTO users(username, password, email) VALUES (:n, 'x', :e) RETURNING id",
            new MapSqlParameterSource().addValue("n", tag).addValue("e", tag + "@example.invalid"),
            Long.class);
  }

  @AfterEach
  void cleanUp() {
    MapSqlParameterSource like = new MapSqlParameterSource("p", tag + "%");
    jdbc.update("DELETE FROM payment_transactions WHERE transaction_code LIKE :p", like);
    jdbc.update("DELETE FROM orders WHERE order_code LIKE :p", like);
    jdbc.update("DELETE FROM users WHERE id = :id", new MapSqlParameterSource("id", chef));
  }

  Long order(String suffix, String status, Duration age) {
    return jdbc.queryForObject(
        """
        INSERT INTO orders(order_code, customer_name, product_name, guest_count, scheduled_at,
                           total_amount, status, customer_id, chef_id, created_at)
        VALUES (:code, 'T', 'M', 2, now() + interval '3 days', 1000, :status, :chef, :chef, :created)
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("code", tag + suffix)
            .addValue("status", status)
            .addValue("chef", chef)
            .addValue("created", System.currentTimeMillis() - age.toMillis()),
        Long.class);
  }

  void attempt(Long orderId, String code, String status, Duration expiresIn) {
    jdbc.update(
        """
        INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, attempt_no, expires_at)
        VALUES (:order, 'VNPAY', :code, 1000, :status, 1, :expires)
        """,
        new MapSqlParameterSource()
            .addValue("order", orderId)
            .addValue("code", code)
            .addValue("status", status)
            .addValue("expires", java.sql.Timestamp.from(OffsetDateTime.now().plus(expiresIn).toInstant())));
  }

  List<String> codesDue(int limit) {
    return payments.findDuePending(OffsetDateTime.now().minusMinutes(2), PageRequest.of(0, limit)).stream()
        .map(PaymentTransaction::getTransactionCode)
        .filter(code -> code.startsWith(tag))
        .toList();
  }

  @Test
  void onlyPendingAttemptsPastExpiryPlusGraceAreDueOldestFirst() {
    attempt(order("O1", "PENDING", Duration.ofMinutes(1)), tag + "OLD", "PENDING", Duration.ofMinutes(-20));
    attempt(order("O2", "PENDING", Duration.ofMinutes(1)), tag + "DUE", "PENDING", Duration.ofMinutes(-5));
    attempt(order("O3", "PENDING", Duration.ofMinutes(1)), tag + "GRACE", "PENDING", Duration.ofSeconds(-90));
    attempt(order("O4", "PENDING", Duration.ofMinutes(1)), tag + "LIVE", "PENDING", Duration.ofMinutes(10));
    attempt(order("O5", "PENDING", Duration.ofMinutes(1)), tag + "PAID", "SUCCESS", Duration.ofMinutes(-30));
    attempt(order("O6", "PENDING", Duration.ofMinutes(1)), tag + "GONE", "CANCELLED", Duration.ofMinutes(-30));

    assertThat(codesDue(50)).containsExactly(tag + "OLD", tag + "DUE");
  }

  @Test
  void theLimitCapsTheNumberOfDueAttempts() {
    for (int i = 0; i < 3; i++) {
      attempt(order("D" + i, "PENDING", Duration.ofMinutes(1)), tag + "D" + i, "PENDING", Duration.ofMinutes(-10 - i));
    }

    assertThat(payments.findDuePending(OffsetDateTime.now().minusMinutes(2), PageRequest.of(0, 2)))
        .hasSize(2);
  }

  List<Long> abandoned(long before, int limit) {
    return pendingOrders.findPendingCreatedBefore(before, PageRequest.of(0, limit));
  }

  List<Long> idsOf(String... suffixes) {
    return jdbc.queryForList(
        "SELECT id FROM orders WHERE order_code IN (:codes) ORDER BY created_at",
        new MapSqlParameterSource("codes", java.util.Arrays.stream(suffixes).map(x -> tag + x).toList()),
        Long.class);
  }

  @Test
  void onlyUnpaidOrdersThatEnteredPaymentAndPassedTheWindowAreReturnedOldestFirst() {
    attempt(order("P1", "PENDING", Duration.ofMinutes(90)), tag + "P1", "PENDING", Duration.ofMinutes(-70));
    attempt(order("P2", "PENDING", Duration.ofMinutes(31)), tag + "P2", "CANCELLED", Duration.ofMinutes(-10));
    attempt(order("P3", "PENDING", Duration.ofMinutes(5)), tag + "P3", "PENDING", Duration.ofMinutes(10));
    attempt(order("P4", "CANCELLED", Duration.ofMinutes(90)), tag + "P4", "CANCELLED", Duration.ofMinutes(-70));
    attempt(order("P5", "CONFIRMED", Duration.ofMinutes(90)), tag + "P5", "SUCCESS", Duration.ofMinutes(-70));
    order("P6", "PENDING", Duration.ofMinutes(90));
    Long paid = order("P7", "PENDING", Duration.ofMinutes(90));
    attempt(paid, tag + "P7A", "CANCELLED", Duration.ofMinutes(-70));
    jdbc.update(
        "INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, attempt_no, expires_at, paid_at)"
            + " VALUES (:o, 'VNPAY', :c, 1000, 'SUCCESS', 2, now(), now())",
        new MapSqlParameterSource().addValue("o", paid).addValue("c", tag + "P7B"));

    long before = System.currentTimeMillis() - Duration.ofMinutes(30).toMillis();
    List<Long> found = abandoned(before, 500);

    assertThat(found).containsSubsequence(idsOf("P1", "P2"));
    assertThat(found)
        .doesNotContainAnyElementsOf(idsOf("P3", "P4", "P5", "P6", "P7"));
    assertThat(abandoned(before, 1)).hasSize(1);
  }

  @Test
  void anOldPendingOrderWithoutAnyAttemptIsNeverReturned() {
    Long seeded = order("S1", "PENDING", Duration.ofDays(3));

    assertThat(abandoned(System.currentTimeMillis(), 500)).doesNotContain(seeded);
  }

  @Test
  void manyOldPaidOrdersCannotStarveANewerAbandonedOne() {
    for (int i = 0; i < 60; i++) {
      Long paid = order("W" + i, "PENDING", Duration.ofHours(5).plusMinutes(i));
      attempt(paid, tag + "W" + i, "SUCCESS", Duration.ofHours(-5));
    }
    Long dead = order("DEAD", "PENDING", Duration.ofMinutes(45));
    attempt(dead, tag + "DEAD", "PENDING", Duration.ofMinutes(-30));

    List<Long> page = abandoned(System.currentTimeMillis() - Duration.ofMinutes(30).toMillis(), 50);

    assertThat(page).contains(dead);
    assertThat(
            page.stream()
                .filter(id -> idsOf("W0", "W1", "W2", "W30", "W59").contains(id))
                .toList())
        .isEmpty();
  }
}
