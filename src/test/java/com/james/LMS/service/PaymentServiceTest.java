package com.james.LMS.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.config.PaymentLifecycleProperties;
import com.james.LMS.config.PaymentProperties;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.pricing.PricingProperties;
import com.james.LMS.pricing.PricingService;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.entity.Product;
import com.james.LMS.entity.User;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentCloseReason;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PendingOrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.response.PaymentIpnResponse;
import com.james.LMS.response.PaymentStatusResponse;
import com.james.LMS.service.PaymentGateway.GatewayTransaction;
import com.james.LMS.service.PaymentGateway.Outcome;
import com.james.LMS.service.PaymentGateway.PaymentRequest;
import com.james.LMS.service.impl.PaymentServiceImpl;
import com.james.LMS.voucher.VoucherService;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentServiceTest {
  static final String CODE = "GC2610147K3QX9";
  static final String TMN = "TESTTMN1";
  static final long CUSTOMER = 5L;
  static final long CHEF = 40L;
  static final long ORDER_ID = 3L;
  static final long PAYMENT_ID = 7L;
  static final BigDecimal AMOUNT = new BigDecimal("240000.00");

  final OrderRepository orders = mock(OrderRepository.class);
  final ProductRepository products = mock(ProductRepository.class);
  final UserRepository users = mock(UserRepository.class);
  final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
  final PaymentGateway gateway = mock(PaymentGateway.class);
  final ObjectMapper mapper = new ObjectMapper();
  final PaymentProperties properties = properties();
  final VoucherService vouchers = mock(VoucherService.class);
  final PendingOrderRepository pendingOrders = mock(PendingOrderRepository.class);
  final PaymentService service =
      new PaymentServiceImpl(
          orders,
          new PricingService(products, new PricingProperties(), vouchers),
          users,
          payments,
          gateway,
          properties,
          mapper,
          new TransactionTemplate(mock(PlatformTransactionManager.class)),
          new PaymentLifecycleProperties(),
          vouchers,
          pendingOrders,
          mock(com.james.LMS.notification.NotificationService.class),
          mock(org.springframework.jdbc.core.JdbcTemplate.class));

  PaymentServiceTest() {
    when(gateway.merchantCode()).thenReturn(TMN);
    when(payments.save(any(PaymentTransaction.class))).thenAnswer(call -> call.getArgument(0));
    when(orders.save(any(CookOrder.class))).thenAnswer(call -> call.getArgument(0));
  }

  static PaymentProperties properties() {
    PaymentProperties.Vnpay vnpay = new PaymentProperties.Vnpay();
    vnpay.setTmnCode(TMN);
    vnpay.setHashSecret("TEST_SECRET_NOT_REAL_0123456789");
    vnpay.setPayUrl("https://sandbox.example.test/pay");
    vnpay.setApiUrl("https://sandbox.example.test/api");
    PaymentProperties properties = new PaymentProperties();
    properties.setFrontendBaseUrl("http://localhost:5173/");
    properties.setVnpay(vnpay);
    return properties;
  }

  static String reference(Duration expiresIn) {
    LocalDateTime now = LocalDateTime.now(PaymentGateway.GATEWAY_ZONE);
    return "{\"createDate\":\"%s\",\"expireDate\":\"%s\"}"
        .formatted(
            now.minusMinutes(1).format(PaymentGateway.GATEWAY_DATE),
            now.plus(expiresIn).format(PaymentGateway.GATEWAY_DATE));
  }

  PaymentTransaction payment(PaymentStatus status, Duration expiresIn) {
    return PaymentTransaction.builder()
        .id(PAYMENT_ID)
        .orderId(ORDER_ID)
        .provider(PaymentProvider.VNPAY)
        .transactionCode(CODE)
        .amount(AMOUNT)
        .status(status)
        .rawReference(reference(expiresIn))
        .expiresAt(OffsetDateTime.now().plus(expiresIn))
        .build();
  }

  CookOrder order(long customerId, OrderStatus status) {
    return CookOrder.builder()
        .id(ORDER_ID)
        .orderCode(CODE)
        .customerId(customerId)
        .chefId(CHEF)
        .productName("Mam com sum vay")
        .guestCount(4)
        .scheduledAt(LocalDateTime.now().plusDays(3))
        .totalAmount(AMOUNT)
        .status(status)
        .createdAt(System.currentTimeMillis())
        .build();
  }

  CookOrder given(PaymentTransaction payment, OrderStatus orderStatus) {
    CookOrder order = order(CUSTOMER, orderStatus);
    when(payments.lockByProviderAndTransactionCode(PaymentProvider.VNPAY, CODE))
        .thenReturn(Optional.of(payment));
    when(payments.findByProviderAndTransactionCode(PaymentProvider.VNPAY, CODE))
        .thenReturn(Optional.of(payment));
    when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
    return order;
  }

  GatewayTransaction callback(
      Outcome outcome, String amount, String payDate, String code, String statusCode) {
    return new GatewayTransaction(
        CODE,
        amount == null ? null : new BigDecimal(amount),
        TMN,
        outcome,
        "14226112",
        payDate,
        code,
        statusCode);
  }

  GatewayTransaction paid() {
    return callback(Outcome.SUCCESS, "240000", "20261014153300", "00", "00");
  }

  void gatewayAnswers(GatewayTransaction tx) {
    when(gateway.verifyCallback(any())).thenReturn(Optional.of(tx));
  }

  Map<String, String> referenceOf(PaymentTransaction payment) throws Exception {
    return mapper.readValue(payment.getRawReference(), new TypeReference<>() {});
  }

  @Test
  void ipnSuccessRecordsPaymentAndRegistersEscrowOnce() throws Exception {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(payment.getPaidAt().toInstant()).isEqualTo(Instant.parse("2026-10-14T08:33:00Z"));
    assertThat(referenceOf(payment))
        .containsEntry("transactionNo", "14226112")
        .containsEntry("paidAtSource", "gateway");
    verify(payments, times(1)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
    verify(orders, never()).save(any());
  }

  @Test
  void duplicateIpnDoesNotRecordOrEscrowTwice() {
    PaymentTransaction payment = payment(PaymentStatus.SUCCESS, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(false);

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("02");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, never()).save(any());
  }

  @Test
  void successWithoutPayDateUsesServerTimeAndSaysSo() throws Exception {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.SUCCESS, "240000", null, "00", "00"));
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    OffsetDateTime before = OffsetDateTime.now();
    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getPaidAt()).isBetween(before, OffsetDateTime.now());
    assertThat(referenceOf(payment)).containsEntry("paidAtSource", "server");
  }

  @Test
  void ipnWithInvalidSignatureIsRejectedWithoutTouchingData() {
    when(gateway.verifyCallback(any())).thenReturn(Optional.empty());

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("97");
    verify(payments, never()).lockByProviderAndTransactionCode(any(), any());
    verify(payments, never()).save(any());
    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void ipnWithWrongAmountIsRejectedAndPaymentStaysPending() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.SUCCESS, "1000", "20261014153300", "00", "00"));

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("04");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void ipnForUnknownReferenceGetsOrderNotFound() {
    gatewayAnswers(paid());
    when(payments.lockByProviderAndTransactionCode(PaymentProvider.VNPAY, CODE))
        .thenReturn(Optional.empty());

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("01");
    verify(payments, never()).save(any());
  }

  @Test
  void ipnForAnotherMerchantIsNotAcceptedAsOurOrder() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(
        new GatewayTransaction(
            CODE, AMOUNT, "OTHERTMN", Outcome.SUCCESS, "1", "20261014153300", "00", "00"));

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("01");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void suspectedFraudCodeKeepsPaymentPendingButStoresGatewayWords() throws Exception {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.UNKNOWN, "240000", "20261014153300", "07", "00"));

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.getPaidAt()).isNull();
    assertThat(referenceOf(payment))
        .containsEntry("transactionNo", "14226112")
        .containsEntry("responseCode", "07");
    verify(payments, times(1)).save(payment);
    verify(payments, never()).registerVerifiedPayment(any(), any());
    verify(orders, never()).save(any());
  }

  @Test
  void customerCancelClosesTheAttemptButKeepsTheOrderOpenForARetry() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    CookOrder order = given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.CANCELLED, "240000", null, "24", "02"));

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(payment.getCloseReason()).isEqualTo(PaymentCloseReason.USER_CANCELLED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void declinedPaymentFailsTheAttemptAndKeepsTheOrderOpenForARetry() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    CookOrder order = given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.FAILED, "240000", null, "51", "02"));

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getCloseReason()).isEqualTo(PaymentCloseReason.GATEWAY_FAILED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void escrowFailureAnswers99ThenNextIpnCallsTheEscrowFunctionAgain() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID))
        .thenThrow(
            new RuntimeException(
                "could not execute query",
                new IllegalStateException("ERROR: New paid order must be PENDING (actual: CONFIRMED)")))
        .thenReturn(true);

    PaymentIpnResponse first = service.handleVnpayIpn(Map.of());

    assertThat(first.rspCode()).isEqualTo("99");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, times(1)).save(payment);

    PaymentIpnResponse retry = service.handleVnpayIpn(Map.of());

    assertThat(retry.rspCode()).isEqualTo("00");
    verify(payments, times(2)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
    verify(payments, times(1)).save(payment);
  }

  @Test
  void orderWithoutChefAnswers99KeepsPaymentSuccessAndNeverRetriesInsideOneCallback() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID))
        .thenThrow(
            new RuntimeException(
                "could not execute query",
                new IllegalStateException("ERROR: Assign chef_id and customer_id before payment escrow for order 3")));

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("99");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, times(1)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
  }

  @Test
  void paymentAmountMismatchInsideTheEscrowFunctionAnswers99AndHoldsNothing() {
    PaymentTransaction payment = payment(PaymentStatus.SUCCESS, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID))
        .thenThrow(
            new RuntimeException(
                "could not execute query",
                new IllegalStateException("ERROR: Payment mismatch for order 3")));

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("99");
    assertThat(service.status(CUSTOMER, CODE, "127.0.0.1").paymentStatus())
        .isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, times(2)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
  }

  @Test
  void queryNotFoundBeforeExpiryDoesNotCancel() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    CookOrder order = given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.NOT_FOUND, null, null, "91", null));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(payments, never()).save(any());
    verify(orders, never()).save(any());
  }

  @Test
  void queryNotFoundAfterExpiryAndGraceClosesTheAttemptAsExpiredAndKeepsTheOrder() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    CookOrder order = given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.NOT_FOUND, null, null, "91", null));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(status.closeReason()).isEqualTo(PaymentCloseReason.EXPIRED);
    assertThat(status.attemptNo()).isEqualTo(1);
    assertThat(status.orderStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void anUnreadableQueryAfterExpiryAndGraceStillClosesTheAttempt() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.UNKNOWN, null, null, null, null));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(status.closeReason()).isEqualTo(PaymentCloseReason.EXPIRED);
  }

  @Test
  void anAttemptInsideItsGraceStaysPendingEvenWhenTheQueryIsUnreadable() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofSeconds(-90));
    given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.UNKNOWN, null, null, null, null));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
  }

  @Test
  void aFailedQueryAfterExpiryAndGraceClosesTheAttemptAsGatewayFailed() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.FAILED, "240000", null, "00", "02"));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(status.closeReason()).isEqualTo(PaymentCloseReason.GATEWAY_FAILED);
  }

  @Test
  void lateSuccessForAnExpiredAttemptSettlesWhenTheOrderIsStillPending() {
    PaymentTransaction payment = payment(PaymentStatus.CANCELLED, Duration.ofMinutes(-5));
    payment.setCloseReason(PaymentCloseReason.EXPIRED);
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(payment.getCloseReason()).isNull();
    assertThat(payment.getPaidAt()).isNotNull();
    verify(payments, times(1)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
    verify(vouchers).consume(ORDER_ID);
  }

  @Test
  void aSecondSuccessForTheSameOrderIsStoredButNotSettled() {
    PaymentTransaction payment = payment(PaymentStatus.CANCELLED, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(payments.findEscrowedPaymentId(ORDER_ID)).thenReturn(Optional.of(PAYMENT_ID - 1));
    gatewayAnswers(paid());

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, never()).registerVerifiedPayment(any(), any());
    verify(vouchers, never()).consume(any());
  }

  @Test
  void aSuccessForAnOrderThatWasCancelledIsStoredButNotSettled() {
    PaymentTransaction payment = payment(PaymentStatus.CANCELLED, Duration.ofMinutes(-5));
    CookOrder order = given(payment, OrderStatus.CANCELLED);
    gatewayAnswers(paid());

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(payments, never()).registerVerifiedPayment(any(), any());
    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("02");
  }

  @Test
  void anUnsettledDuplicateIsNotHealedOnEveryStatusPoll() {
    PaymentTransaction payment = payment(PaymentStatus.SUCCESS, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(payments.findEscrowedPaymentId(ORDER_ID)).thenReturn(Optional.of(PAYMENT_ID - 1));

    service.status(CUSTOMER, CODE, "127.0.0.1");
    service.status(CUSTOMER, CODE, "127.0.0.1");

    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void whenThePayWindowHasPassedTheOrderIsCancelledAndTheVoucherIsReleased() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    CookOrder order = given(payment, OrderStatus.PENDING);
    ReflectionTestUtils.setField(
        order, "createdAt", System.currentTimeMillis() - Duration.ofMinutes(31).toMillis());
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.NOT_FOUND, null, null, "91", null));
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenReturn(List.of(payment));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(status.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(payment.getCloseReason()).isEqualTo(PaymentCloseReason.EXPIRED);
    verify(vouchers).release(ORDER_ID);
  }

  @Test
  void thePayWindowDoesNotCancelWhileAnAttemptCouldStillSucceed() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofSeconds(-30));
    CookOrder order = given(payment, OrderStatus.PENDING);
    ReflectionTestUtils.setField(
        order, "createdAt", System.currentTimeMillis() - Duration.ofMinutes(31).toMillis());
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.UNKNOWN, null, null, null, null));
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenReturn(List.of(payment));

    service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(vouchers, never()).release(any());
  }

  @Test
  void queryFailureBeforeExpiryLeavesPaymentPendingSoCustomerCanStillPay() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.FAILED, "240000", null, "00", "02"));

    assertThat(service.status(CUSTOMER, CODE, "127.0.0.1").paymentStatus())
        .isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
  }

  // ---------- return URL (browser comes back from VNPay) ----------

  Map<String, String> returnParams() {
    return Map.of("vnp_TxnRef", CODE);
  }

  @Test
  void aSignedCancelOnTheReturnUrlClosesTheAttemptAndKeepsTheOrderForARetry() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    CookOrder order = given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.CANCELLED, "240000", "20261022062540", "24", "02"));

    PaymentStatusResponse response = service.handleVnpayReturn(CUSTOMER, returnParams());

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(payment.getCloseReason()).isEqualTo(PaymentCloseReason.USER_CANCELLED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(response.closeReason()).isEqualTo(PaymentCloseReason.USER_CANCELLED);
    assertThat(response.orderStatus()).isEqualTo(OrderStatus.PENDING);
    verify(gateway, never()).query(any(), any(), any());
    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void aRepeatedSignedCancelIsIdempotent() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.CANCELLED, "240000", null, "24", "02"));

    PaymentStatusResponse first = service.handleVnpayReturn(CUSTOMER, returnParams());
    PaymentStatusResponse second = service.handleVnpayReturn(CUSTOMER, returnParams());

    assertThat(second).isEqualTo(first);
    verify(payments, times(1)).save(payment);
  }

  @Test
  void returnParametersWithABadSignatureChangeNothing() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    when(gateway.verifyCallback(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.handleVnpayReturn(CUSTOMER, returnParams()))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RETURN_INVALID_SIGNATURE));

    when(gateway.verifyCallback(any())).thenThrow(new IllegalArgumentException("bad amount"));
    assertThatThrownBy(() -> service.handleVnpayReturn(CUSTOMER, returnParams()))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RETURN_INVALID_SIGNATURE));
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
  }

  @Test
  void aReturnForAnotherCustomersPaymentLooksLikeNotFoundJustLikeStatus() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.CANCELLED, "240000", null, "24", "02"));

    assertThatThrownBy(() -> service.handleVnpayReturn(99L, returnParams()))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
  }

  @Test
  void aReturnForAnotherMerchantOrAmountIsRejectedAndChangesNothing() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(
        new GatewayTransaction(CODE, AMOUNT, "OTHERTMN", Outcome.CANCELLED, "0", null, "24", "02"));
    assertThatThrownBy(() -> service.handleVnpayReturn(CUSTOMER, returnParams()))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RETURN_MISMATCH));

    gatewayAnswers(callback(Outcome.CANCELLED, "1000", null, "24", "02"));
    assertThatThrownBy(() -> service.handleVnpayReturn(CUSTOMER, returnParams()))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RETURN_MISMATCH));
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
  }

  @Test
  void aSignedSuccessReturnSettlesAndAttemptsEscrowWhenIpnCannotReachLocalhost() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    PaymentStatusResponse response = service.handleVnpayReturn(CUSTOMER, returnParams());

    assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(payment.getPaidAt()).isNotNull();
    verify(payments).save(payment);
    verify(payments).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
    verify(vouchers).consume(ORDER_ID);
  }

  @Test
  void signedGatewayFailureIsRecordedAsFailedWithoutEscrow() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.FAILED, "240000", null, "51", "02"));

    PaymentStatusResponse response = service.handleVnpayReturn(CUSTOMER, returnParams());

    assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(payments).save(payment);
    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void aPaymentThatSucceedsAfterTheCancelReturnIsStillSettledByIpn() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.CANCELLED, "240000", null, "24", "02"));
    service.handleVnpayReturn(CUSTOMER, returnParams());
    gatewayAnswers(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("00");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, times(1)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
  }

  // ---------- sweeper ----------

  @Test
  void theSweepClosesADueAttemptAfterOneLastQuery() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(payments.findDuePending(any(), any())).thenReturn(List.of(payment));
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.UNKNOWN, null, null, null, null));

    PaymentService.SweepResult result = service.sweepExpired(50);

    assertThat(result).isEqualTo(new PaymentService.SweepResult(1, 0));
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(payment.getCloseReason()).isEqualTo(PaymentCloseReason.EXPIRED);
    verify(gateway, times(1)).query(any(), any(), any());
  }

  @Test
  void theSweepSettlesAnAttemptThatTurnsOutToBePaid() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(payments.findDuePending(any(), any())).thenReturn(List.of(payment));
    when(gateway.query(any(), any(), any())).thenReturn(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    service.sweepExpired(50);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, times(1)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
  }

  @Test
  void theSweepOnlyLooksAtAttemptsPastExpiryPlusGraceAndAtMostTheLimit() {
    java.time.OffsetDateTime before = java.time.OffsetDateTime.now();
    when(payments.findDuePending(any(), any())).thenReturn(List.of());

    service.sweepExpired(50);

    ArgumentCaptor<java.time.OffsetDateTime> cutoff = ArgumentCaptor.forClass(java.time.OffsetDateTime.class);
    ArgumentCaptor<org.springframework.data.domain.Pageable> page =
        ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
    verify(payments).findDuePending(cutoff.capture(), page.capture());
    assertThat(cutoff.getValue())
        .isBetween(before.minusMinutes(2).minusSeconds(1), java.time.OffsetDateTime.now().minusMinutes(2));
    assertThat(page.getValue().getPageSize()).isEqualTo(50);
    verify(gateway, never()).query(any(), any(), any());
  }

  @Test
  void theSweepCancelsAnUnpaidOrderPastItsPayWindowAndReleasesTheVoucher() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    CookOrder order = given(payment, OrderStatus.PENDING);
    ReflectionTestUtils.setField(
        order, "createdAt", System.currentTimeMillis() - Duration.ofMinutes(31).toMillis());
    when(payments.findDuePending(any(), any())).thenReturn(List.of());
    when(pendingOrders.findPendingCreatedBefore(org.mockito.ArgumentMatchers.anyLong(), any()))
        .thenReturn(List.of(ORDER_ID));
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenReturn(List.of(payment));
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.NOT_FOUND, null, null, "91", null));

    PaymentService.SweepResult result = service.sweepExpired(50);

    assertThat(result).isEqualTo(new PaymentService.SweepResult(0, 1));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(payment.getCloseReason()).isEqualTo(PaymentCloseReason.EXPIRED);
    verify(vouchers).release(ORDER_ID);
  }

  @Test
  void theSweepNeverTouchesAnOldPendingOrderThatNeverEnteredPayment() {
    PaymentTransaction unused = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    CookOrder order = given(unused, OrderStatus.PENDING);
    ReflectionTestUtils.setField(
        order, "createdAt", System.currentTimeMillis() - Duration.ofHours(5).toMillis());
    when(payments.findDuePending(any(), any())).thenReturn(List.of());
    when(pendingOrders.findPendingCreatedBefore(org.mockito.ArgumentMatchers.anyLong(), any()))
        .thenReturn(List.of(ORDER_ID));
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenReturn(List.of());

    PaymentService.SweepResult result = service.sweepExpired(50);

    assertThat(result).isEqualTo(new PaymentService.SweepResult(0, 0));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(orders, never()).save(any());
    verify(vouchers, never()).release(any());
    verify(gateway, never()).query(any(), any(), any());
  }

  @Test
  void theSweepNeverTouchesAnOldPaidOrderThatIsStillWaitingForTheChef() {
    PaymentTransaction paid = payment(PaymentStatus.SUCCESS, Duration.ofMinutes(-300));
    CookOrder order = given(paid, OrderStatus.PENDING);
    ReflectionTestUtils.setField(
        order, "createdAt", System.currentTimeMillis() - Duration.ofHours(5).toMillis());
    when(payments.findDuePending(any(), any())).thenReturn(List.of());
    when(pendingOrders.findPendingCreatedBefore(org.mockito.ArgumentMatchers.anyLong(), any()))
        .thenReturn(List.of(ORDER_ID));
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenReturn(List.of(paid));

    PaymentService.SweepResult result = service.sweepExpired(50);

    assertThat(result).isEqualTo(new PaymentService.SweepResult(0, 0));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    verify(orders, never()).save(any());
    verify(vouchers, never()).release(any());
  }

  @Test
  void theSweepLeavesAnOrderAloneWhileAnAttemptIsStillInsideItsGrace() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofSeconds(-30));
    CookOrder order = given(payment, OrderStatus.PENDING);
    ReflectionTestUtils.setField(
        order, "createdAt", System.currentTimeMillis() - Duration.ofMinutes(31).toMillis());
    when(payments.findDuePending(any(), any())).thenReturn(List.of());
    when(pendingOrders.findPendingCreatedBefore(org.mockito.ArgumentMatchers.anyLong(), any()))
        .thenReturn(List.of(ORDER_ID));
    when(payments.findByOrderIdOrderByAttemptNoAsc(ORDER_ID)).thenReturn(List.of(payment));

    PaymentService.SweepResult result = service.sweepExpired(50);

    assertThat(result).isEqualTo(new PaymentService.SweepResult(0, 0));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(vouchers, never()).release(any());
  }

  @Test
  void querySuccessIsRecordedAndEscrowRegistered() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any())).thenReturn(paid());
    when(payments.registerVerifiedPayment(ORDER_ID, PAYMENT_ID)).thenReturn(true);

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(payments, times(1)).registerVerifiedPayment(ORDER_ID, PAYMENT_ID);
  }

  @Test
  void statusOfAnotherCustomersOrderLooksLikeNotFound() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    given(payment, OrderStatus.PENDING);

    assertThatThrownBy(() -> service.status(99L, CODE, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));
    verify(gateway, never()).query(any(), any(), any());
  }

  Product product(ProductStatus status) {
    return Product.builder()
        .id(11L)
        .name("Mam com sum vay")
        .category("Mon Viet")
        .unitPrice(new BigDecimal("120000.00"))
        .minGuests(2)
        .maxGuests(6)
        .status(status)
        .sellerId(CHEF)
        .build();
  }

  PaymentCheckoutRequest checkoutRequest(int guests, LocalDateTime at) {
    return new PaymentCheckoutRequest(
        11L, guests, at, "Ngoc Anh", "0901234567", "12 Nguyen Hue, Quan 1", null, UUID.randomUUID(), null);
  }

  LocalDateTime nextWeek() {
    return LocalDateTime.now(PaymentGateway.GATEWAY_ZONE).plusDays(7);
  }

  void orderGetsAnId() {
    when(orders.saveAndFlush(any(CookOrder.class)))
        .thenAnswer(
            call -> {
              CookOrder saved = call.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", ORDER_ID);
              return saved;
            });
  }

  @Test
  void checkoutComputesTheAmountOnTheServerAndIgnoresAnyAmountFromTheClient() throws Exception {
    when(products.findById(11L)).thenReturn(Optional.of(product(ProductStatus.PUBLISHED)));
    when(users.findById(CHEF)).thenReturn(Optional.of(User.builder().username("Co Mai").build()));
    when(gateway.createPaymentUrl(any())).thenReturn("https://pay.example.test/go");
    orderGetsAnId();

    ObjectMapper webMapper = Jackson2ObjectMapperBuilder.json().build();
    String body =
        """
        {"productId":11,"guestCount":3,"scheduledAt":"%s","customerName":"Ngoc Anh",
         "customerPhone":"0901234567","serviceAddress":"12 Nguyen Hue",
         "idempotencyKey":"0f8fad5b-d9cb-469f-a165-70867728950e",
         "amount":1,"totalAmount":1,"price":1,"unitPrice":1,"total":1}
        """
            .formatted(nextWeek().withNano(0));
    PaymentCheckoutRequest request = webMapper.readValue(body, PaymentCheckoutRequest.class);
    assertThat(
            Arrays.stream(PaymentCheckoutRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .doesNotContain("amount", "totalAmount", "price", "unitPrice", "total");

    PaymentCheckoutResponse response = service.checkout(CUSTOMER, "a@example.test", request, "1.2.3.4");

    ArgumentCaptor<CookOrder> savedOrder = ArgumentCaptor.forClass(CookOrder.class);
    verify(orders).saveAndFlush(savedOrder.capture());
    ArgumentCaptor<PaymentTransaction> savedPayment =
        ArgumentCaptor.forClass(PaymentTransaction.class);
    verify(payments).save(savedPayment.capture());
    ArgumentCaptor<PaymentRequest> toGateway = ArgumentCaptor.forClass(PaymentRequest.class);
    verify(gateway).createPaymentUrl(toGateway.capture());

    BigDecimal expected = new BigDecimal("360000");
    CookOrder order = savedOrder.getValue();
    assertThat(order.getTotalAmount()).isEqualByComparingTo(expected);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getCustomerId()).isEqualTo(CUSTOMER);
    assertThat(order.getChefId()).isEqualTo(CHEF);
    assertThat(order.getCustomerPhone()).isEqualTo("0901234567");
    assertThat(order.getChefName()).isEqualTo("Co Mai");
    assertThat(savedPayment.getValue().getAmount()).isEqualByComparingTo(expected);
    assertThat(savedPayment.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(savedPayment.getValue().getProvider()).isEqualTo(PaymentProvider.VNPAY);
    assertThat(savedPayment.getValue().getTransactionCode()).isEqualTo(order.getOrderCode());
    assertThat(toGateway.getValue().amount()).isEqualByComparingTo(expected);
    assertThat(toGateway.getValue().txnRef()).isEqualTo(order.getOrderCode());
    assertThat(toGateway.getValue().returnUrl()).isEqualTo("http://localhost:5173/payment-result");
    assertThat(toGateway.getValue().ipAddress()).isEqualTo("1.2.3.4");
    assertThat(response.orderCode()).matches("GC\\d{6}[A-Z0-9]{6}");
    assertThat(response.payUrl()).isEqualTo("https://pay.example.test/go");
  }

  @Test
  void checkoutRejectsGuestCountOutsideProductLimits() {
    when(products.findById(11L)).thenReturn(Optional.of(product(ProductStatus.PUBLISHED)));

    for (int guests : List.of(1, 7)) {
      assertThatThrownBy(
              () -> service.checkout(CUSTOMER, "a@example.test", checkoutRequest(guests, nextWeek()), "ip"))
          .isInstanceOfSatisfying(
              PaymentException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_GUESTS));
    }
    verify(orders, never()).saveAndFlush(any());
    verify(payments, never()).save(any());
  }

  @Test
  void checkoutRejectsProductThatIsNotPublished() {
    when(products.findById(11L)).thenReturn(Optional.of(product(ProductStatus.DRAFT)));

    assertThatThrownBy(
            () -> service.checkout(CUSTOMER, "a@example.test", checkoutRequest(3, nextWeek()), "ip"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_PRODUCT_UNAVAILABLE));
    verify(orders, never()).saveAndFlush(any());
  }

  @Test
  void checkoutRejectsAScheduleInThePast() {
    when(products.findById(11L)).thenReturn(Optional.of(product(ProductStatus.PUBLISHED)));
    LocalDateTime past = LocalDateTime.now(PaymentGateway.GATEWAY_ZONE).minusHours(1);

    assertThatThrownBy(
            () -> service.checkout(CUSTOMER, "a@example.test", checkoutRequest(3, past), "ip"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_SCHEDULE));
    verify(orders, never()).saveAndFlush(any());
  }
}
