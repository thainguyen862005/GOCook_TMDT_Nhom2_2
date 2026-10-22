package com.james.LMS.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.config.PaymentLifecycleProperties;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentCloseReason;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.pricing.PricingProperties;
import com.james.LMS.pricing.PricingService;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PendingOrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentOrderCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.service.PaymentGateway.GatewayTransaction;
import com.james.LMS.service.PaymentGateway.Outcome;
import com.james.LMS.service.PaymentGateway.PaymentRequest;
import com.james.LMS.service.impl.PaymentServiceImpl;
import com.james.LMS.voucher.VoucherService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Paying an order that already exists as PENDING, including retries after a failed attempt. */
class PaymentOrderCheckoutTest {
  static final long OWNER = 5L;
  static final long STRANGER = 99L;
  static final long ORDER_ID = 8L;
  static final String CODE = "GC261102ABCDE1";

  final OrderRepository orders = mock(OrderRepository.class);
  final UserRepository users = mock(UserRepository.class);
  final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
  final PaymentGateway gateway = mock(PaymentGateway.class);
  final VoucherService vouchers = mock(VoucherService.class);
  final PaymentService service =
      new PaymentServiceImpl(
          orders,
          new PricingService(mock(ProductRepository.class), new PricingProperties(), vouchers),
          users,
          payments,
          gateway,
          PaymentServiceTest.properties(),
          new ObjectMapper(),
          new TransactionTemplate(mock(PlatformTransactionManager.class)),
          new PaymentLifecycleProperties(),
          vouchers,
          mock(PendingOrderRepository.class));

  final List<PaymentTransaction> saved = new ArrayList<>();

  PaymentOrderCheckoutTest() {
    when(payments.save(any(PaymentTransaction.class)))
        .thenAnswer(
            call -> {
              PaymentTransaction payment = call.getArgument(0);
              if (payment.getId() == null) {
                ReflectionTestUtils.setField(payment, "id", 100L + saved.size());
                saved.add(payment);
              }
              return payment;
            });
    when(payments.findByIdempotencyKey(any()))
        .thenAnswer(
            call ->
                saved.stream().filter(p -> call.getArgument(0).equals(p.getIdempotencyKey())).findFirst());
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenAnswer(call -> List.copyOf(saved));
    when(payments.lockByProviderAndTransactionCode(any(), any()))
        .thenAnswer(
            call ->
                saved.stream().filter(p -> call.getArgument(1).equals(p.getTransactionCode())).findFirst());
    when(gateway.createPaymentUrl(any()))
        .thenAnswer(call -> "https://pay.example.test/" + call.<PaymentRequest>getArgument(0).txnRef());
  }

  CookOrder order(OrderStatus status, Long chefId, Long customerId, Duration age) {
    CookOrder order =
        CookOrder.builder()
            .orderCode(CODE)
            .customerId(customerId)
            .chefId(chefId)
            .productName("Bao gia 21")
            .guestCount(6)
            .scheduledAt(LocalDateTime.now().plusDays(3))
            .totalAmount(new BigDecimal("1200000.00"))
            .status(status)
            .createdAt(System.currentTimeMillis() - age.toMillis())
            .build();
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    return order;
  }

  CookOrder existing(CookOrder order) {
    when(orders.findByOrderCode(CODE)).thenReturn(Optional.of(order));
    when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
    return order;
  }

  CookOrder pendingOrder() {
    return existing(order(OrderStatus.PENDING, 40L, OWNER, Duration.ofMinutes(1)));
  }

  /** An earlier attempt of the order, already in the fake store. */
  PaymentTransaction earlier(int attemptNo, PaymentStatus status, Duration expiresIn) {
    LocalDateTime now = LocalDateTime.now(PaymentGateway.GATEWAY_ZONE);
    PaymentTransaction payment =
        PaymentTransaction.builder()
            .orderId(ORDER_ID)
            .provider(PaymentProvider.VNPAY)
            .transactionCode(attemptNo == 1 ? CODE : CODE + "A" + attemptNo)
            .amount(new BigDecimal("1200000.00"))
            .status(status)
            .attemptNo(attemptNo)
            .expiresAt(OffsetDateTime.now().plus(expiresIn))
            .rawReference(
                "{\"createDate\":\"%s\",\"expireDate\":\"%s\"}"
                    .formatted(
                        now.minusMinutes(16).format(PaymentGateway.GATEWAY_DATE),
                        now.plus(expiresIn).format(PaymentGateway.GATEWAY_DATE)))
            .idempotencyKey(UUID.randomUUID())
            .build();
    ReflectionTestUtils.setField(payment, "id", 50L + attemptNo);
    saved.add(payment);
    return payment;
  }

  PaymentOrderCheckoutRequest request(UUID key) {
    return new PaymentOrderCheckoutRequest(CODE, key);
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void theFirstAttemptUsesTheOrderCodeAsReferenceAndTheOrderTotal() {
    CookOrder order = pendingOrder();
    UUID key = UUID.randomUUID();

    PaymentCheckoutResponse response = service.checkoutOrder(OWNER, request(key), "1.2.3.4");

    assertThat(response.orderCode()).isEqualTo(CODE);
    assertThat(response.payUrl()).isEqualTo("https://pay.example.test/" + CODE);
    PaymentTransaction attempt = saved.get(0);
    assertThat(attempt.getAttemptNo()).isEqualTo(1);
    assertThat(attempt.getTransactionCode()).isEqualTo(CODE);
    assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(attempt.getAmount()).isEqualByComparingTo("1200000");
    assertThat(attempt.getIdempotencyKey()).isEqualTo(key);
    assertThat(attempt.getExpiresAt()).isBetween(OffsetDateTime.now().plusMinutes(14), OffsetDateTime.now().plusMinutes(16));
    verify(orders, never()).save(any());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void aRetryAfterACancelledAttemptOpensAttemptTwoWithItsOwnReferenceAndKey() {
    pendingOrder();
    PaymentTransaction first = earlier(1, PaymentStatus.CANCELLED, Duration.ofMinutes(-1));
    first.setCloseReason(PaymentCloseReason.USER_CANCELLED);
    UUID key = UUID.randomUUID();

    PaymentCheckoutResponse response = service.checkoutOrder(OWNER, request(key), "1.2.3.4");

    assertThat(response.orderCode()).isEqualTo(CODE);
    assertThat(response.payUrl()).isEqualTo("https://pay.example.test/" + CODE + "A2");
    PaymentTransaction retry = saved.get(saved.size() - 1);
    assertThat(retry.getAttemptNo()).isEqualTo(2);
    assertThat(retry.getTransactionCode()).isEqualTo(CODE + "A2");
    assertThat(retry.getTransactionCode()).isNotEqualTo(first.getTransactionCode());
    assertThat(retry.getIdempotencyKey()).isEqualTo(key).isNotEqualTo(first.getIdempotencyKey());
    assertThat(retry.getAmount()).isEqualByComparingTo("1200000");
    assertThat(retry.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void theThirdAttemptGetsTheNextSuffix() {
    pendingOrder();
    earlier(1, PaymentStatus.CANCELLED, Duration.ofMinutes(-1));
    earlier(2, PaymentStatus.FAILED, Duration.ofMinutes(-1));

    service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip");

    assertThat(saved.get(saved.size() - 1).getTransactionCode()).isEqualTo(CODE + "A3");
  }

  @Test
  void anUnexpiredPendingAttemptIsReplayedInsteadOfOpeningAnotherOne() {
    pendingOrder();
    PaymentTransaction open = earlier(1, PaymentStatus.PENDING, Duration.ofMinutes(10));

    PaymentCheckoutResponse response = service.checkoutOrder(OWNER, request(UUID.randomUUID()), "1.2.3.4");

    assertThat(response.payUrl()).isEqualTo("https://pay.example.test/" + open.getTransactionCode());
    assertThat(saved).hasSize(1);
    verify(payments, never()).save(any(PaymentTransaction.class));
  }

  @Test
  void anAttemptPastExpiryAndGraceIsClosedAfterOneLastQueryThenARetryOpens() {
    pendingOrder();
    PaymentTransaction stale = earlier(1, PaymentStatus.PENDING, Duration.ofMinutes(-5));
    when(gateway.query(any(), any(), any()))
        .thenReturn(new GatewayTransaction(CODE, null, null, Outcome.NOT_FOUND, null, null, "91", null));

    service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip");

    verify(gateway).query(any(), any(), any());
    assertThat(stale.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(stale.getCloseReason()).isEqualTo(PaymentCloseReason.EXPIRED);
    assertThat(saved.get(saved.size() - 1).getAttemptNo()).isEqualTo(2);
  }

  @Test
  void ifTheLastQueryShowsTheStaleAttemptWasPaidNoRetryIsOpened() {
    pendingOrder();
    PaymentTransaction stale = earlier(1, PaymentStatus.PENDING, Duration.ofMinutes(-5));
    when(gateway.merchantCode()).thenReturn("TMN");
    when(gateway.query(any(), any(), any()))
        .thenReturn(
            new GatewayTransaction(
                CODE, new BigDecimal("1200000"), "TMN", Outcome.SUCCESS, "77", "20261102100000", "00", "00"));
    when(payments.registerVerifiedPayment(ORDER_ID, stale.getId())).thenReturn(true);

    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);

    assertThat(stale.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(saved).hasSize(1);
  }

  @Test
  void aPaidOrderCannotBePaidAgain() {
    pendingOrder();
    earlier(1, PaymentStatus.SUCCESS, Duration.ofMinutes(10));

    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    assertThat(saved).hasSize(1);
  }

  @Test
  void afterThePayWindowAnOrderThatNeverEnteredPaymentCannotBePaidButIsNotCancelled() {
    CookOrder order = existing(order(OrderStatus.PENDING, 40L, OWNER, Duration.ofMinutes(31)));

    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_WINDOW_PASSED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(vouchers, never()).release(any());
    assertThat(saved).isEmpty();
  }

  @Test
  void afterThePayWindowAnOrderWhoseAttemptsAllFailedIsCancelledAndCannotBePaid() {
    CookOrder order = existing(order(OrderStatus.PENDING, 40L, OWNER, Duration.ofMinutes(31)));
    earlier(1, PaymentStatus.CANCELLED, Duration.ofMinutes(-20));

    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_WINDOW_PASSED);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(vouchers).release(ORDER_ID);
  }

  @Test
  void theNewAttemptNeverOutlivesThePayWindow() {
    existing(order(OrderStatus.PENDING, 40L, OWNER, Duration.ofMinutes(25)));

    service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip");

    assertThat(saved.get(0).getExpiresAt())
        .isBefore(OffsetDateTime.now().plusMinutes(6))
        .isAfter(OffsetDateTime.now().plusMinutes(3));
  }

  @Test
  void sameKeyTwiceByTheOwnerOpensOnePayment() {
    pendingOrder();
    UUID key = UUID.randomUUID();

    PaymentCheckoutResponse first = service.checkoutOrder(OWNER, request(key), "1.2.3.4");
    PaymentCheckoutResponse second = service.checkoutOrder(OWNER, request(key), "1.2.3.4");

    assertThat(second).isEqualTo(first);
    assertThat(saved).hasSize(1);
    verify(payments, times(1)).save(any(PaymentTransaction.class));
  }

  @Test
  void sameKeyFromAnotherCustomerIsRejected() {
    pendingOrder();
    UUID key = UUID.randomUUID();
    service.checkoutOrder(OWNER, request(key), "1.2.3.4");
    when(orders.findByOrderCode(CODE))
        .thenReturn(Optional.of(order(OrderStatus.PENDING, 40L, STRANGER, Duration.ofMinutes(1))));

    assertRejected(
        () -> service.checkoutOrder(STRANGER, request(key), "1.2.3.4"),
        ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    assertThat(saved).hasSize(1);
  }

  @Test
  void anotherCustomersOrAnUnknownOrderLooksLikeNotFound() {
    pendingOrder();

    assertRejected(
        () -> service.checkoutOrder(STRANGER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_NOT_FOUND);
    assertRejected(
        () -> service.checkoutOrder(OWNER, new PaymentOrderCheckoutRequest("GC000000NOPE00", UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_NOT_FOUND);
    assertThat(saved).isEmpty();
  }

  @Test
  void onlyAPendingOrderWithAChefCanBePaid() {
    for (OrderStatus status : new OrderStatus[] {OrderStatus.CONFIRMED, OrderStatus.COMPLETED}) {
      existing(order(status, 40L, OWNER, Duration.ofMinutes(1)));
      assertRejected(
          () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
          ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    }
    existing(order(OrderStatus.PENDING, null, OWNER, Duration.ofMinutes(1)));
    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    assertThat(saved).isEmpty();
  }

  @Test
  void theGatewayIsGivenTheAttemptReferenceAndAmount() {
    pendingOrder();
    earlier(1, PaymentStatus.FAILED, Duration.ofMinutes(-1));

    service.checkoutOrder(OWNER, request(UUID.randomUUID()), "1.2.3.4");

    ArgumentCaptor<PaymentRequest> sent = ArgumentCaptor.forClass(PaymentRequest.class);
    verify(gateway).createPaymentUrl(sent.capture());
    assertThat(sent.getValue().txnRef()).isEqualTo(CODE + "A2");
    assertThat(sent.getValue().amount()).isEqualByComparingTo("1200000");
    assertThat(sent.getValue().txnRef()).matches("[A-Za-z0-9]{1,100}");
  }
}
