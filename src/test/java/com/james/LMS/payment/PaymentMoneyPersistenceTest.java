package com.james.LMS.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.adminread.PaymentAdminReadService;
import com.james.LMS.payment.adminread.PaymentAdminReadService.PaymentDetail;
import com.james.LMS.payment.escrow.EscrowRepository;
import com.james.LMS.payment.escrow.EscrowService;
import com.james.LMS.payment.escrow.EscrowService.ReleaseResult;
import com.james.LMS.payment.finance.FinanceRange;
import com.james.LMS.payment.finance.SellerFinanceService;
import com.james.LMS.payment.finance.SellerFinanceService.MonthlyIncome;
import com.james.LMS.payment.finance.SellerFinanceService.Summary;
import com.james.LMS.payment.finance.SellerFinanceService.TransactionPage;
import com.james.LMS.refund.CancellationContext;
import com.james.LMS.refund.CancelledBy;
import com.james.LMS.refund.Refund;
import com.james.LMS.refund.RefundGateway;
import com.james.LMS.refund.RefundGateway.RefundOutcome;
import com.james.LMS.refund.RefundPolicy;
import com.james.LMS.refund.RefundProperties;
import com.james.LMS.refund.RefundService;
import com.james.LMS.refund.RefundStatus;
import com.james.LMS.refund.RefundStore;
import com.james.LMS.refund.SimulatedRefundGateway;
import com.james.LMS.voucher.VoucherProperties;
import com.james.LMS.voucher.VoucherService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Release, refund, seller finance and the admin read model against a real PostgreSQL with the real
 * SQL functions. Runs only when GOCOOK_PAYMENT_TEST_URL points to a throwaway database named like
 * sprint2b_verify.
 */
@DataJpaTest(
    properties = {
      "spring.datasource.url=${GOCOOK_PAYMENT_TEST_URL}",
      "spring.datasource.username=app_user",
      "spring.datasource.password=${GOCOOK_PAYMENT_TEST_PASSWORD:app_password}",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.show-sql=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GOCOOK_PAYMENT_TEST_URL", matches = ".+sprint2b_verify.*")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentMoneyPersistenceTest {
  static final ZoneOffset VN = ZoneOffset.ofHours(7);

  @Autowired NamedParameterJdbcTemplate jdbc;
  @Autowired EscrowRepository escrowRepository;
  @Autowired PlatformTransactionManager transactionManager;

  String tag;
  Long chef;
  Long otherChef;
  Long customer;
  final List<Long> orderIds = new ArrayList<>();

  EscrowService escrow;
  RefundService refunds;
  RefundService failingRefunds;
  SellerFinanceService finance;
  PaymentAdminReadService adminRead;

  record Paid(Long orderId, Long paymentId, String code) {}

  @BeforeEach
  void wire() {
    tag = "RT" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    chef = user("chef");
    otherChef = user("other");
    customer = user("cust");
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    RefundStore store = new RefundStore(jdbc);
    RefundProperties properties = new RefundProperties();
    VoucherService vouchers = new VoucherService(jdbc, new VoucherProperties());
    RefundPolicy policy = new RefundPolicy(properties);
    RefundGateway failing = request -> RefundOutcome.failure("bank unavailable");
    escrow = new EscrowService(escrowRepository, tx);
    refunds = new RefundService(store, policy, new SimulatedRefundGateway(), vouchers, tx);
    failingRefunds = new RefundService(store, policy, failing, vouchers, tx);
    finance = new SellerFinanceService(jdbc);
    adminRead = new PaymentAdminReadService(jdbc, properties);
  }

  @AfterEach
  void cleanUp() {
    for (Long id : orderIds) {
      MapSqlParameterSource byOrder = new MapSqlParameterSource("id", id);
      jdbc.update("DELETE FROM refunds WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM escrow_ledger_entries WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM order_escrows WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM admin_refund_requests WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM admin_cases WHERE booking_id = :id", byOrder);
      jdbc.update("DELETE FROM payment_transactions WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM order_status_history WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM chef_bookings WHERE order_id = :id", byOrder);
      jdbc.update("DELETE FROM orders WHERE id = :id", byOrder);
    }
    for (Long id : new Long[] {chef, otherChef, customer}) {
      jdbc.update("DELETE FROM users WHERE id = :id", new MapSqlParameterSource("id", id));
    }
  }

  Long user(String role) {
    return jdbc.queryForObject(
        "INSERT INTO users(username, password, email) VALUES (:n, 'x', :e) RETURNING id",
        new MapSqlParameterSource()
            .addValue("n", tag + role)
            .addValue("e", tag + role + "@example.invalid"),
        Long.class);
  }

  /** A paid order: PENDING order, SUCCESS payment and a HELD escrow made by the real function. */
  Paid paid(String suffix, Long chefId, LocalDateTime cooking) {
    String code = tag + suffix;
    Long orderId =
        jdbc.queryForObject(
            """
            INSERT INTO orders(order_code, customer_name, product_name, guest_count, scheduled_at,
                               total_amount, status, customer_id, chef_id)
            VALUES (:code, 'T', 'M', 4, :cooking, 1000000, 'PENDING', :customer, :chef) RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("cooking", cooking)
                .addValue("customer", customer)
                .addValue("chef", chefId),
            Long.class);
    orderIds.add(orderId);
    Long paymentId = payment(orderId, code, 1, "SUCCESS");
    jdbc.queryForObject(
        "SELECT gocook_register_verified_payment(:o, :p)",
        new MapSqlParameterSource().addValue("o", orderId).addValue("p", paymentId),
        Boolean.class);
    return new Paid(orderId, paymentId, code);
  }

  Long payment(Long orderId, String txnRef, int attempt, String status) {
    return jdbc.queryForObject(
        """
        INSERT INTO payment_transactions(order_id, provider, transaction_code, amount, status, attempt_no,
                                         expires_at, paid_at)
        VALUES (:o, 'VNPAY', :ref, 1000000, :status, :attempt, now(),
                CASE WHEN :status = 'SUCCESS' THEN now() END)
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("o", orderId)
            .addValue("ref", txnRef)
            .addValue("status", status)
            .addValue("attempt", attempt),
        Long.class);
  }

  void setOrderStatus(Long orderId, String status) {
    jdbc.update(
        "UPDATE orders SET status = :s WHERE id = :id",
        new MapSqlParameterSource().addValue("s", status).addValue("id", orderId));
  }

  Map<String, Object> escrowRow(Long orderId) {
    return jdbc.queryForMap(
        "SELECT status, released_at FROM order_escrows WHERE order_id = :id",
        new MapSqlParameterSource("id", orderId));
  }

  long ledger(Long orderId, String entry) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM escrow_ledger_entries WHERE order_id = :id AND entry_type = :t",
        new MapSqlParameterSource().addValue("id", orderId).addValue("t", entry),
        Long.class);
  }

  String paymentStatus(Long paymentId) {
    return jdbc.queryForObject(
        "SELECT status FROM payment_transactions WHERE id = :id",
        new MapSqlParameterSource("id", paymentId),
        String.class);
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  CancellationContext customerCancels(Paid paid, long hoursBefore, LocalDateTime cooking) {
    return new CancellationContext(
        paid.orderId(), CancelledBy.CUSTOMER, cooking.minusHours(hoursBefore).atOffset(VN), cooking, "doi y");
  }

  static final LocalDateTime COOKING = LocalDateTime.of(2030, 1, 10, 18, 0);

  // ---------------- P12 release ----------------

  @Test
  void releaseMovesTheOrderToCompletedOnceAndWritesOneLedgerRow() {
    Paid paid = paid("R1", chef, COOKING);
    setOrderStatus(paid.orderId(), "AWAITING_ACCEPTANCE");

    assertThat(escrow.release(paid.orderId(), customer)).isEqualTo(ReleaseResult.RELEASED);
    assertThat(escrow.release(paid.orderId(), customer)).isEqualTo(ReleaseResult.ALREADY_RELEASED);

    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("RELEASED");
    assertThat(escrowRow(paid.orderId()).get("released_at")).isNotNull();
    assertThat(ledger(paid.orderId(), "RELEASED")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = :id", new MapSqlParameterSource("id", paid.orderId()), String.class))
        .isEqualTo("COMPLETED");
  }

  @Test
  void theRealFunctionMessagesAreMappedToCodedErrors() {
    Paid pending = paid("R2", chef, COOKING);
    assertRejected(() -> escrow.release(pending.orderId(), customer), ErrorCode.ESCROW_ORDER_NOT_AWAITING);
    assertRejected(() -> escrow.release(pending.orderId(), otherChef), ErrorCode.ESCROW_NOT_OWNER);
    assertRejected(() -> escrow.release(-5L, customer), ErrorCode.ESCROW_ORDER_NOT_FOUND);

    setOrderStatus(pending.orderId(), "AWAITING_ACCEPTANCE");
    jdbc.update(
        "UPDATE order_escrows SET status = 'REFUND_PENDING' WHERE order_id = :id",
        new MapSqlParameterSource("id", pending.orderId()));
    assertRejected(() -> escrow.release(pending.orderId(), customer), ErrorCode.ESCROW_NOT_HELD);
  }

  @Test
  void anOpenCaseBlocksTheReleaseUntilItIsResolved() {
    Paid paid = paid("R3", chef, COOKING);
    setOrderStatus(paid.orderId(), "AWAITING_ACCEPTANCE");
    Long caseId =
        jdbc.queryForObject(
            "INSERT INTO admin_cases(case_code, subject, description, booking_id, status) VALUES (:c, 's', 'd', :o, 'OPEN') RETURNING id",
            new MapSqlParameterSource().addValue("c", tag + "CASE").addValue("o", paid.orderId()),
            Long.class);

    assertRejected(() -> escrow.release(paid.orderId(), customer), ErrorCode.ESCROW_DISPUTE_OPEN);
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("HELD");

    jdbc.update("UPDATE admin_cases SET status = 'IN_REVIEW' WHERE id = :id", new MapSqlParameterSource("id", caseId));
    assertRejected(() -> escrow.release(paid.orderId(), customer), ErrorCode.ESCROW_DISPUTE_OPEN);

    jdbc.update("UPDATE admin_cases SET status = 'RESOLVED' WHERE id = :id", new MapSqlParameterSource("id", caseId));
    assertThat(escrow.release(paid.orderId(), customer)).isEqualTo(ReleaseResult.RELEASED);
  }

  // ---------------- P13 refund ----------------

  @Test
  void aFullRefundWalksHeldThroughRefundPendingToRefundedAndRepeatsHarmlessly() {
    Paid paid = paid("F1", chef, COOKING);
    setOrderStatus(paid.orderId(), "CANCELLED");

    Refund first = refunds.requestRefund(customerCancels(paid, 49, COOKING)).orElseThrow();
    Refund second = refunds.requestRefund(customerCancels(paid, 49, COOKING)).orElseThrow();

    assertThat(first.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(first.amount()).isEqualByComparingTo("1000000");
    assertThat(first.gatewayReference()).isEqualTo("SIM-" + first.id());
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUNDED");
    assertThat(paymentStatus(paid.paymentId())).isEqualTo("REFUNDED");
    assertThat(ledger(paid.orderId(), "REFUND_PENDING")).isEqualTo(1);
    assertThat(ledger(paid.orderId(), "REFUNDED")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM refunds WHERE order_id = :id", new MapSqlParameterSource("id", paid.orderId()), Long.class))
        .isEqualTo(1);
  }

  @Test
  void aPartialRefundKeepsThePaymentSuccessfulAndRecordsTheRetainedMoney() {
    Paid paid = paid("F2", chef, COOKING);

    Refund refund = refunds.requestRefund(customerCancels(paid, 30, COOKING)).orElseThrow();

    assertThat(refund.amount()).isEqualByComparingTo("500000");
    assertThat(refund.retainedAmount()).isEqualByComparingTo("500000");
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUNDED");
    assertThat(paymentStatus(paid.paymentId())).isEqualTo("SUCCESS");
  }

  @Test
  void theChefRejectFunctionLeavesRefundPendingAndTheRefundCompletesIt() {
    Paid paid = paid("F3", chef, COOKING);
    jdbc.queryForObject(
        "SELECT gocook_chef_reject_order(:o, :c, 'ban')",
        new MapSqlParameterSource().addValue("o", paid.orderId()).addValue("c", chef),
        Boolean.class);
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUND_PENDING");

    Refund refund =
        refunds
            .requestRefund(new CancellationContext(paid.orderId(), CancelledBy.CHEF, OffsetDateTime.now(), COOKING, "ban"))
            .orElseThrow();

    assertThat(refund.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(refund.amount()).isEqualByComparingTo("1000000");
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUNDED");
    assertThat(ledger(paid.orderId(), "REFUND_PENDING")).isEqualTo(1);
    assertThat(ledger(paid.orderId(), "REFUNDED")).isEqualTo(1);
  }

  @Test
  void aGatewayFailureKeepsRefundPendingAndARetrySucceeds() {
    Paid paid = paid("F4", chef, COOKING);

    Refund failed = failingRefunds.requestRefund(customerCancels(paid, 49, COOKING)).orElseThrow();

    assertThat(failed.status()).isEqualTo(RefundStatus.FAILED);
    assertThat(failed.failureMessage()).isEqualTo("bank unavailable");
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUND_PENDING");
    assertThat(paymentStatus(paid.paymentId())).isEqualTo("SUCCESS");
    assertRejected(() -> refunds.retry(-1L), ErrorCode.REFUND_NOT_FOUND);

    Refund retried = refunds.retry(failed.id());

    assertThat(retried.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUNDED");
    assertThat(paymentStatus(paid.paymentId())).isEqualTo("REFUNDED");
    assertRejected(() -> refunds.retry(failed.id()), ErrorCode.REFUND_NOT_RETRYABLE);
    assertThat(refunds.requestRefund(customerCancels(paid, 49, COOKING)).orElseThrow().id()).isEqualTo(failed.id());
  }

  @Test
  void aReleasedOrderCanNoLongerBeRefunded() {
    Paid paid = paid("F5", chef, COOKING);
    setOrderStatus(paid.orderId(), "AWAITING_ACCEPTANCE");
    escrow.release(paid.orderId(), customer);

    assertRejected(() -> refunds.requestRefund(customerCancels(paid, 49, COOKING)), ErrorCode.REFUND_ESCROW_RELEASED);
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("RELEASED");
  }

  @Test
  void anApprovedAdminRequestIsExecutedOnceAndOnlyStatusReferenceAndTimeChange() {
    Paid paid = paid("A1", chef, COOKING);
    Long requestId =
        jdbc.queryForObject(
            "INSERT INTO admin_refund_requests(order_id, amount, reason, status) VALUES (:o, 400000, 'khach khieu nai', 'REVIEWED') RETURNING id",
            new MapSqlParameterSource("o", paid.orderId()),
            Long.class);
    Long versionBefore =
        jdbc.queryForObject("SELECT version FROM admin_refund_requests WHERE id = :id", new MapSqlParameterSource("id", requestId), Long.class);

    Refund first = refunds.executeApproved(requestId);
    Refund second = refunds.executeApproved(requestId);

    assertThat(first.amount()).isEqualByComparingTo("400000");
    assertThat(second.id()).isEqualTo(first.id());
    Map<String, Object> admin =
        jdbc.queryForMap(
            "SELECT status, gateway_reference, version FROM admin_refund_requests WHERE id = :id",
            new MapSqlParameterSource("id", requestId));
    assertThat(admin.get("status")).isEqualTo("EXECUTED");
    assertThat(admin.get("gateway_reference")).isEqualTo("SIM-" + first.id());
    assertThat(admin.get("version")).isEqualTo(versionBefore);
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("REFUNDED");
  }

  @Test
  void aDuplicatePaymentIsRefundedWithoutTouchingTheEscrow() {
    Paid paid = paid("D1", chef, COOKING);
    Long duplicate = payment(paid.orderId(), paid.code() + "A2", 2, "SUCCESS");

    Refund refund = refunds.refundDuplicate(duplicate);

    assertThat(refund.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(refund.amount()).isEqualByComparingTo("1000000");
    assertThat(paymentStatus(duplicate)).isEqualTo("REFUNDED");
    assertThat(paymentStatus(paid.paymentId())).isEqualTo("SUCCESS");
    assertThat(escrowRow(paid.orderId()).get("status")).isEqualTo("HELD");
    assertThat(refunds.refundDuplicate(duplicate).id()).isEqualTo(refund.id());
    assertRejected(() -> refunds.refundDuplicate(paid.paymentId()), ErrorCode.REFUND_NOT_DUPLICATE);
  }

  // ---------------- P14 seller finance ----------------

  void escrowIs(Paid paid, String status, OffsetDateTime releasedAt) {
    jdbc.update(
        "UPDATE order_escrows SET status = :s, released_at = :t, updated_at = now() WHERE order_id = :id",
        new MapSqlParameterSource()
            .addValue("s", status)
            .addValue("t", releasedAt)
            .addValue("id", paid.orderId()));
  }

  @Test
  void onlyHeldAndReleasedMoneyCountsAndTheMonthBoundaryIsVietnamMidnight() {
    Paid held = paid("S1", chef, COOKING);
    Paid october = paid("S2", chef, COOKING);
    Paid november = paid("S3", chef, COOKING);
    Paid refunded = paid("S4", chef, COOKING);
    Paid waiting = paid("S5", chef, COOKING);
    Paid foreign = paid("S6", otherChef, COOKING);
    escrowIs(october, "RELEASED", OffsetDateTime.parse("2026-10-31T23:30:00+07:00"));
    escrowIs(november, "RELEASED", OffsetDateTime.parse("2026-11-01T00:30:00+07:00"));
    escrowIs(refunded, "REFUNDED", null);
    escrowIs(waiting, "REFUND_PENDING", null);
    escrowIs(foreign, "RELEASED", OffsetDateTime.parse("2026-10-15T10:00:00+07:00"));

    Summary all = finance.summary(chef, FinanceRange.of(null, null));
    Summary onlyOctober = finance.summary(chef, FinanceRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    Summary onlyNovember = finance.summary(chef, FinanceRange.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));

    assertThat(all.availableAmount()).isEqualByComparingTo("1700000");
    assertThat(all.pendingAmount()).isEqualByComparingTo("850000");
    assertThat(all.platformFeeTotal()).isEqualByComparingTo("300000");
    assertThat(all.releasedOrderCount()).isEqualTo(2);
    assertThat(all.menuOrderCount() + all.budgetOrderCount()).isEqualTo(3);
    assertThat(onlyOctober.availableAmount()).isEqualByComparingTo("850000");
    assertThat(onlyOctober.releasedOrderCount()).isEqualTo(1);
    assertThat(onlyNovember.availableAmount()).isEqualByComparingTo("850000");
    assertThat(onlyNovember.releasedOrderCount()).isEqualTo(1);
  }

  @Test
  void transactionsListOnlyHeldAndReleasedWithTheFeePercentFromTheRow() {
    Paid held = paid("T1", chef, COOKING);
    Paid released = paid("T2", chef, COOKING);
    Paid refunded = paid("T3", chef, COOKING);
    escrowIs(released, "RELEASED", OffsetDateTime.now());
    escrowIs(refunded, "REFUNDED", null);

    TransactionPage page = finance.transactions(chef, 0, 5000, null, FinanceRange.of(null, null));

    assertThat(page.size()).isEqualTo(100);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content()).extracting(t -> t.orderCode()).containsExactlyInAnyOrder(held.code(), released.code());
    assertThat(page.content()).allSatisfy(t -> assertThat(t.platformFeePercent()).isEqualByComparingTo("15.00"));
    assertThat(page.content()).allSatisfy(t -> assertThat(t.heldAt()).endsWith("+07:00"));
    assertThat(finance.transactions(chef, 0, 20, "HELD", FinanceRange.of(null, null)).content())
        .extracting(t -> t.orderCode())
        .containsExactly(held.code());
  }

  @Test
  void theMonthlyIncomeFillsMonthsWithoutIncomeWithZero() {
    Paid thisMonth = paid("M1", chef, COOKING);
    escrowIs(thisMonth, "RELEASED", OffsetDateTime.now());

    List<MonthlyIncome> months = finance.monthly(chef, 3);

    assertThat(months).hasSize(3);
    assertThat(months.get(2).month()).isEqualTo(YearMonth.now(ZoneOffset.ofHours(7)).toString());
    assertThat(months.get(2).earnedAmount()).isEqualByComparingTo("850000");
    assertThat(months.get(0).earnedAmount()).isEqualByComparingTo("0");
    assertThat(months.get(1).orderCount()).isZero();
  }

  // ---------------- P15 admin read model ----------------

  @Test
  void theAdminListFindsAPaymentByReferenceAndAnUnknownReferenceGivesAnEmptyPage() {
    Paid paid = paid("L1", chef, COOKING);

    assertThat(adminRead.payments(null, null, null, paid.code(), null, null, 0, 20).content())
        .extracting(p -> p.orderCode())
        .containsExactly(paid.code());
    assertThat(adminRead.payments(null, null, null, "NO-SUCH-" + tag, null, null, 0, 20).content()).isEmpty();
    assertThat(adminRead.payments(null, null, null, null, null, null, 0, 5000).size()).isEqualTo(100);
    PaymentDetail detail = adminRead.payment(paid.paymentId());
    assertThat(detail.escrow().status()).isEqualTo("HELD");
    assertThat(detail.payment().createdAt()).endsWith("+07:00");
    assertRejected(() -> adminRead.payment(-1L), ErrorCode.ADMIN_PAYMENT_NOT_FOUND);
  }

  @Test
  void reconciliationListsTheMoneyThatDoesNotAddUp() {
    Paid duplicated = paid("C1", chef, COOKING);
    Long second = payment(duplicated.orderId(), duplicated.code() + "A2", 2, "SUCCESS");
    Paid orphan = paid("C2", chef, COOKING);
    jdbc.update(
        "UPDATE payment_transactions SET status = 'CANCELLED' WHERE id = :id",
        new MapSqlParameterSource("id", orphan.paymentId()));
    Paid stale = paid("C3", chef, COOKING);
    escrowIs(stale, "REFUND_PENDING", null);
    jdbc.update(
        "UPDATE order_escrows SET updated_at = now() - interval '3 days' WHERE order_id = :id",
        new MapSqlParameterSource("id", stale.orderId()));

    var report = adminRead.reconciliation();

    assertThat(report.successWithoutEscrow()).extracting(p -> p.paymentId()).contains(second);
    assertThat(report.ordersWithMultipleSuccess()).extracting(o -> o.orderCode()).contains(duplicated.code());
    assertThat(report.heldEscrowWithoutSuccess()).extracting(h -> h.orderCode()).contains(orphan.code());
    assertThat(report.staleRefundPending()).extracting(s -> s.orderCode()).contains(stale.code());
    assertThat(report.successWithoutEscrow()).extracting(p -> p.orderCode()).doesNotContain(stale.code());
  }
}
