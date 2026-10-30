package com.james.LMS.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.refund.RefundGateway.RefundOutcome;
import com.james.LMS.refund.RefundStore.AdminRequestRow;
import com.james.LMS.refund.RefundStore.EscrowRow;
import com.james.LMS.refund.RefundStore.NewRefund;
import com.james.LMS.refund.RefundStore.OrderRow;
import com.james.LMS.refund.RefundStore.PaymentRow;
import com.james.LMS.voucher.VoucherService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class RefundServiceTest {
  static final long ORDER = 8L;
  static final long PAYMENT = 21L;
  static final long ESCROW = 31L;
  static final BigDecimal GROSS = new BigDecimal("1000000.00");
  static final LocalDateTime COOKING = LocalDateTime.of(2026, 10, 24, 18, 0);

  final RefundStore store = mock(RefundStore.class);
  final RefundGateway gateway = mock(RefundGateway.class);
  final VoucherService vouchers = mock(VoucherService.class);
  final RefundService service =
      new RefundService(
          store,
          new RefundPolicy(new RefundProperties()),
          gateway,
          vouchers,
          new TransactionTemplate(mock(PlatformTransactionManager.class)));

  /** The refunds table in memory: just enough for insert, status changes and reads. */
  final AtomicReference<Refund> row = new AtomicReference<>();

  RefundServiceTest() {
    when(store.lockOrder(ORDER)).thenReturn(Optional.of(new OrderRow(ORDER, "GC261022ABCDE1")));
    when(store.lockPayment(PAYMENT)).thenReturn(Optional.of(payment("SUCCESS", GROSS)));
    when(store.latestForPayment(PAYMENT)).thenAnswer(call -> Optional.ofNullable(row.get()));
    when(store.find(anyLong())).thenAnswer(call -> row.get());
    when(store.peek(anyLong())).thenAnswer(call -> Optional.ofNullable(row.get()));
    when(store.lockRefund(anyLong())).thenAnswer(call -> Optional.ofNullable(row.get()));
    when(store.byAdminRequest(anyLong())).thenAnswer(call -> Optional.ofNullable(row.get()).filter(r -> r.adminRefundRequestId() != null));
    when(store.insert(any(NewRefund.class)))
        .thenAnswer(
            call -> {
              NewRefund n = call.getArgument(0);
              row.set(
                  new Refund(
                      900L, n.orderId(), n.paymentId(), n.amount(), n.retained(), n.percent(),
                      n.reason(), RefundStatus.REQUESTED, null, null, n.adminRequestId(),
                      OffsetDateTime.now(), OffsetDateTime.now()));
              return row.get();
            });
    org.mockito.Mockito.doAnswer(call -> {
          update(RefundStatus.REFUNDED, call.getArgument(1), null);
          return null;
        })
        .when(store)
        .markRefunded(anyLong(), any());
    org.mockito.Mockito.doAnswer(call -> {
          update(RefundStatus.FAILED, null, call.getArgument(1));
          return null;
        })
        .when(store)
        .markFailed(anyLong(), anyString());
    org.mockito.Mockito.doAnswer(call -> {
          update(RefundStatus.REQUESTED, null, null);
          return null;
        })
        .when(store)
        .markRequested(anyLong());
    when(gateway.refund(any())).thenAnswer(call -> RefundOutcome.success("SIM-" + row.get().id()));
  }

  static org.mockito.ArgumentMatcher<BigDecimal> sameAs(String value) {
    return actual -> actual != null && actual.compareTo(new BigDecimal(value)) == 0;
  }

  void update(RefundStatus status, String reference, String message) {
    Refund r = row.get();
    row.set(
        new Refund(
            r.id(), r.orderId(), r.paymentTransactionId(), r.amount(), r.retainedAmount(),
            r.policyPercent(), r.reasonCode(), status, reference, message, r.adminRefundRequestId(),
            r.createdAt(), OffsetDateTime.now()));
  }

  static PaymentRow payment(String status, BigDecimal amount) {
    return new PaymentRow(PAYMENT, ORDER, status, amount);
  }

  EscrowRow escrow(String status) {
    return new EscrowRow(
        ESCROW, ORDER, PAYMENT, status, GROSS, new BigDecimal("150000.00"), new BigDecimal("850000.00"));
  }

  void escrowIs(String status) {
    when(store.lockEscrow(ORDER)).thenReturn(Optional.of(escrow(status)));
  }

  CancellationContext customerCancels(long hoursBefore) {
    return new CancellationContext(
        ORDER, CancelledBy.CUSTOMER,
        COOKING.minusHours(hoursBefore).atOffset(ZoneOffset.ofHours(7)), COOKING, "doi y");
  }

  CancellationContext chefRejects() {
    return new CancellationContext(
        ORDER, CancelledBy.CHEF, COOKING.minusHours(5).atOffset(ZoneOffset.ofHours(7)), COOKING, "ban");
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void automaticChefCancellationQueuesRefundButNeverClaimsMoneyTransferred() {
    escrowIs("HELD");
    Refund refund = service.queueRefund(chefRejects()).orElseThrow();
    assertThat(refund.status()).isEqualTo(RefundStatus.REQUESTED);
    assertThat(refund.amount()).isEqualByComparingTo("1000000");
    verify(store).holdForRefund(any(), anyString());
    verify(gateway, never()).refund(any());
    verify(store, never()).markRefunded(anyLong(), any());
    verify(store, never()).paymentRefunded(anyLong());
  }

  @Test
  void automaticCancellationReusesExistingRefundWithoutAnotherAttempt() {
    escrowIs("REFUND_PENDING");
    Refund first = service.queueRefund(chefRejects()).orElseThrow();
    Refund second = service.queueRefund(chefRejects()).orElseThrow();
    assertThat(second.id()).isEqualTo(first.id());
    verify(store, times(1)).insert(any(NewRefund.class));
    verify(gateway, never()).refund(any());
  }

  @Test
  void aChefRejectionAfterPaymentRefundsEverythingAndLocksInOrderEscrowPayment() {
    escrowIs("REFUND_PENDING");

    Refund refund = service.requestRefund(chefRejects()).orElseThrow();

    assertThat(refund.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(refund.reasonCode()).isEqualTo(RefundReason.CHEF_REJECTED);
    assertThat(refund.amount()).isEqualByComparingTo("1000000");
    assertThat(refund.retainedAmount()).isEqualByComparingTo("0");
    assertThat(refund.gatewayReference()).isEqualTo("SIM-900");
    verify(store, never()).holdForRefund(any(), any());
    verify(store).escrowRefunded(any(), argThat(sameAs("1000000")), anyString());
    verify(store).paymentRefunded(PAYMENT);
    verify(vouchers).returnAfterRefund(ORDER);
    InOrder locks = inOrder(store);
    locks.verify(store).lockOrder(ORDER);
    locks.verify(store).lockEscrow(ORDER);
    locks.verify(store).lockPayment(PAYMENT);
  }

  @Test
  void aCustomerCancellingFortyNineHoursAheadMovesHeldMoneyThroughRefundPendingToRefunded() {
    escrowIs("HELD");

    Refund refund = service.requestRefund(customerCancels(49)).orElseThrow();

    assertThat(refund.amount()).isEqualByComparingTo("1000000");
    assertThat(refund.policyPercent()).isEqualTo(100);
    InOrder steps = inOrder(store);
    steps.verify(store).holdForRefund(any(), anyString());
    steps.verify(store).markRefunded(900L, "SIM-900");
    steps.verify(store).escrowRefunded(any(), any(), anyString());
    steps.verify(store).paymentRefunded(PAYMENT);
  }

  @Test
  void aPartialRefundKeepsThePaymentSuccessfulAndRecordsWhatIsRetained() {
    escrowIs("HELD");

    Refund refund = service.requestRefund(customerCancels(30)).orElseThrow();

    assertThat(refund.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(refund.policyPercent()).isEqualTo(50);
    assertThat(refund.amount()).isEqualByComparingTo("500000");
    assertThat(refund.retainedAmount()).isEqualByComparingTo("500000");
    verify(store).escrowRefunded(any(), argThat(sameAs("500000")), anyString());
    verify(store, never()).paymentRefunded(anyLong());
    verify(vouchers, never()).returnAfterRefund(anyLong());
  }

  @Test
  void theZeroPercentTierRecordsTheRetentionWithoutCallingTheGateway() {
    escrowIs("HELD");

    Refund refund = service.requestRefund(customerCancels(2)).orElseThrow();

    assertThat(refund.status()).isEqualTo(RefundStatus.REFUNDED);
    assertThat(refund.amount()).isEqualByComparingTo("0");
    assertThat(refund.retainedAmount()).isEqualByComparingTo("1000000");
    verify(gateway, never()).refund(any());
    verify(store, never()).paymentRefunded(anyLong());
    verify(store).escrowRefunded(any(), any(), anyString());
  }

  @Test
  void aGatewayFailureLeavesTheEscrowWaitingAndAnAdminCanRetry() {
    escrowIs("HELD");
    doReturn(RefundOutcome.failure("bank unavailable")).when(gateway).refund(any());

    Refund failed = service.requestRefund(customerCancels(49)).orElseThrow();

    assertThat(failed.status()).isEqualTo(RefundStatus.FAILED);
    assertThat(failed.failureMessage()).isEqualTo("bank unavailable");
    verify(store).holdForRefund(any(), anyString());
    verify(store, never()).escrowRefunded(any(), any(), anyString());
    verify(store, never()).paymentRefunded(anyLong());

    escrowIs("REFUND_PENDING");
    doReturn(RefundOutcome.success("SIM-900")).when(gateway).refund(any());

    Refund retried = service.retry(900L);

    assertThat(retried.status()).isEqualTo(RefundStatus.REFUNDED);
    verify(store).escrowRefunded(any(), any(), anyString());
    verify(store).paymentRefunded(PAYMENT);
  }

  @Test
  void onlyAFailedRefundCanBeRetried() {
    escrowIs("HELD");
    service.requestRefund(customerCancels(49));

    assertRejected(() -> service.retry(900L), ErrorCode.REFUND_NOT_RETRYABLE);
    when(store.peek(404L)).thenReturn(Optional.empty());
    assertRejected(() -> service.retry(404L), ErrorCode.REFUND_NOT_FOUND);
  }

  @Test
  void aRepeatedRequestReturnsTheExistingRefundAndPaysNothingTwice() {
    escrowIs("HELD");
    Refund first = service.requestRefund(customerCancels(49)).orElseThrow();

    Refund second = service.requestRefund(customerCancels(49)).orElseThrow();

    assertThat(second).isEqualTo(first);
    verify(store, times(1)).insert(any());
    verify(gateway, times(1)).refund(any());
  }

  @Test
  void aRefundAfterTheMoneyWasReleasedIsRefused() {
    escrowIs("RELEASED");

    assertRejected(() -> service.requestRefund(customerCancels(49)), ErrorCode.REFUND_ESCROW_RELEASED);
    verify(store, never()).insert(any());
    verify(gateway, never()).refund(any());
  }

  @Test
  void anOrderNeverPaidHasNothingToRefund() {
    when(store.lockEscrow(ORDER)).thenReturn(Optional.empty());
    when(store.hasSuccessPayment(ORDER)).thenReturn(false);

    assertThat(service.requestRefund(customerCancels(49))).isEmpty();
  }

  @Test
  void aSuccessfulPaymentWithoutEscrowIsAnErrorNotASilentSkip() {
    when(store.lockEscrow(ORDER)).thenReturn(Optional.empty());
    when(store.hasSuccessPayment(ORDER)).thenReturn(true);

    assertRejected(() -> service.requestRefund(customerCancels(49)), ErrorCode.REFUND_NO_ESCROW);
  }

  // ---------- admin approved ----------

  void adminRequest(String status, String amount) {
    when(store.lockAdminRequest(77L))
        .thenReturn(Optional.of(new AdminRequestRow(77L, ORDER, new BigDecimal(amount), status)));
  }

  @Test
  void anApprovedAdminRequestRefundsItsAmountAndIsMarkedExecuted() {
    escrowIs("HELD");
    adminRequest("REVIEWED", "400000.00");

    Refund refund = service.executeApproved(77L);

    assertThat(refund.reasonCode()).isEqualTo(RefundReason.ADMIN_APPROVED);
    assertThat(refund.amount()).isEqualByComparingTo("400000");
    assertThat(refund.retainedAmount()).isEqualByComparingTo("600000");
    assertThat(refund.policyPercent()).isEqualTo(40);
    assertThat(refund.adminRefundRequestId()).isEqualTo(77L);
    verify(store).markAdminExecuted(77L, "SIM-900");
    verify(store, never()).paymentRefunded(anyLong());
  }

  @Test
  void executingAnAdminRequestTwiceReturnsTheSameRefund() {
    escrowIs("HELD");
    adminRequest("REVIEWED", "1000000.00");
    Refund first = service.executeApproved(77L);

    adminRequest("EXECUTED", "1000000.00");
    Refund second = service.executeApproved(77L);

    assertThat(second).isEqualTo(first);
    verify(store, times(1)).insert(any());
    verify(gateway, times(1)).refund(any());
  }

  @Test
  void anAdminRequestThatIsNotReviewedOrIsTooBigIsRefused() {
    escrowIs("HELD");
    adminRequest("PENDING", "100000.00");
    assertRejected(() -> service.executeApproved(77L), ErrorCode.REFUND_ADMIN_REQUEST_INVALID);
    adminRequest("REJECTED", "100000.00");
    assertRejected(() -> service.executeApproved(77L), ErrorCode.REFUND_ADMIN_REQUEST_INVALID);

    adminRequest("REVIEWED", "1000001.00");
    assertRejected(() -> service.executeApproved(77L), ErrorCode.REFUND_AMOUNT_EXCEEDS);
    verify(store, never()).insert(any());
    verify(store, never()).markAdminExecuted(anyLong(), any());
  }

  @Test
  void aFailedAdminRefundStaysReviewedAndTheSecondExecutionRetriesIt() {
    escrowIs("HELD");
    adminRequest("REVIEWED", "1000000.00");
    doReturn(RefundOutcome.failure("bank unavailable")).when(gateway).refund(any());
    Refund failed = service.executeApproved(77L);
    assertThat(failed.status()).isEqualTo(RefundStatus.FAILED);
    verify(store, never()).markAdminExecuted(anyLong(), any());

    escrowIs("REFUND_PENDING");
    doReturn(RefundOutcome.success("SIM-900")).when(gateway).refund(any());
    Refund retried = service.executeApproved(77L);

    assertThat(retried.status()).isEqualTo(RefundStatus.REFUNDED);
    verify(store).markAdminExecuted(77L, "SIM-900");
    verify(store, times(1)).insert(any());
  }

  @Test
  void anUnknownAdminRequestIsRefused() {
    when(store.lockAdminRequest(404L)).thenReturn(Optional.empty());

    assertRejected(() -> service.executeApproved(404L), ErrorCode.REFUND_ADMIN_REQUEST_INVALID);
  }

  // ---------- duplicate payment ----------

  static final long DUPLICATE = 22L;

  void duplicateFixture(String paymentStatus, Optional<Long> escrowed, String orderStatus) {
    when(store.paymentOrderId(DUPLICATE)).thenReturn(Optional.of(ORDER));
    when(store.lockPayment(DUPLICATE))
        .thenReturn(Optional.of(new PaymentRow(DUPLICATE, ORDER, paymentStatus, GROSS)));
    when(store.latestForPayment(DUPLICATE)).thenAnswer(call -> Optional.ofNullable(row.get()));
    when(store.escrowedPaymentId(ORDER)).thenReturn(escrowed);
    when(store.orderStatus(ORDER)).thenReturn(orderStatus);
  }

  @Test
  void aDuplicatePaymentIsRefundedInFullWithoutTouchingTheEscrow() {
    duplicateFixture("SUCCESS", Optional.of(PAYMENT), "PENDING");

    Refund refund = service.refundDuplicate(DUPLICATE);

    assertThat(refund.reasonCode()).isEqualTo(RefundReason.DUPLICATE_PAYMENT);
    assertThat(refund.amount()).isEqualByComparingTo("1000000");
    assertThat(refund.status()).isEqualTo(RefundStatus.REFUNDED);
    verify(store, never()).lockEscrow(anyLong());
    verify(store, never()).holdForRefund(any(), any());
    verify(store, never()).escrowRefunded(any(), any(), any());
    verify(store).paymentRefunded(DUPLICATE);
    verify(vouchers, never()).returnAfterRefund(anyLong());
  }

  @Test
  void aSuccessAfterTheOrderWasCancelledIsAlsoARefundableDuplicate() {
    duplicateFixture("SUCCESS", Optional.empty(), "CANCELLED");

    assertThat(service.refundDuplicate(DUPLICATE).status()).isEqualTo(RefundStatus.REFUNDED);
  }

  @Test
  void thePaymentHeldInEscrowOrOneStillWaitingToSettleIsNotADuplicate() {
    duplicateFixture("SUCCESS", Optional.of(DUPLICATE), "PENDING");
    assertRejected(() -> service.refundDuplicate(DUPLICATE), ErrorCode.REFUND_NOT_DUPLICATE);

    duplicateFixture("SUCCESS", Optional.empty(), "PENDING");
    assertRejected(() -> service.refundDuplicate(DUPLICATE), ErrorCode.REFUND_NOT_DUPLICATE);

    duplicateFixture("CANCELLED", Optional.of(PAYMENT), "PENDING");
    assertRejected(() -> service.refundDuplicate(DUPLICATE), ErrorCode.REFUND_NOT_DUPLICATE);
    verify(store, never()).insert(any());
  }

  @Test
  void refundingTheSameDuplicateTwiceDoesNotPayTwice() {
    duplicateFixture("SUCCESS", Optional.of(PAYMENT), "PENDING");
    Refund first = service.refundDuplicate(DUPLICATE);

    Refund second = service.refundDuplicate(DUPLICATE);

    assertThat(second).isEqualTo(first);
    verify(gateway, times(1)).refund(any());
  }

  @Test
  void theSimulatedGatewayAnswersWithAReferenceBuiltFromTheRefundId() {
    RefundOutcome outcome =
        new SimulatedRefundGateway()
            .refund(new RefundGateway.RefundRequest(42L, 7L, BigDecimal.TEN, RefundReason.CUSTOMER_CANCEL));

    assertThat(outcome.success()).isTrue();
    assertThat(outcome.reference()).isEqualTo("SIM-42");
  }
}
