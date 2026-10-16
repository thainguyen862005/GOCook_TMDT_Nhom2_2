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
import com.james.LMS.config.PaymentProperties;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.entity.Product;
import com.james.LMS.entity.User;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.repository.OrderRepository;
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
  final PaymentService service =
      new PaymentServiceImpl(
          orders,
          products,
          users,
          payments,
          gateway,
          properties,
          mapper,
          new TransactionTemplate(mock(PlatformTransactionManager.class)));

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
  void customerCancelCancelsPaymentAndOrderWithoutEscrow() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    CookOrder order = given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.CANCELLED, "240000", null, "24", "02"));

    PaymentIpnResponse reply = service.handleVnpayIpn(Map.of());

    assertThat(reply.rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    verify(payments, never()).registerVerifiedPayment(any(), any());
  }

  @Test
  void declinedPaymentFailsPaymentAndCancelsOrder() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(10));
    CookOrder order = given(payment, OrderStatus.PENDING);
    gatewayAnswers(callback(Outcome.FAILED, "240000", null, "51", "02"));

    assertThat(service.handleVnpayIpn(Map.of()).rspCode()).isEqualTo("00");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
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
  void queryNotFoundAfterExpiryCancelsPaymentAndOrder() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    CookOrder order = given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.NOT_FOUND, null, null, "91", null));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(status.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void ambiguousOrFailedQueryKeepsPaymentPendingEvenAfterExpiry() {
    PaymentTransaction payment = payment(PaymentStatus.PENDING, Duration.ofMinutes(-5));
    given(payment, OrderStatus.PENDING);
    when(gateway.query(any(), any(), any()))
        .thenReturn(callback(Outcome.UNKNOWN, null, null, null, null));

    PaymentStatusResponse status = service.status(CUSTOMER, CODE, "127.0.0.1");

    assertThat(status.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(payments, never()).save(any());
    verify(orders, never()).save(any());
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
        11L, guests, at, "Ngoc Anh", "0901234567", "12 Nguyen Hue, Quan 1", null);
  }

  LocalDateTime nextWeek() {
    return LocalDateTime.now(PaymentGateway.GATEWAY_ZONE).plusDays(7);
  }

  void orderGetsAnId() {
    when(orders.save(any(CookOrder.class)))
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
    verify(orders).save(savedOrder.capture());
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
    verify(orders, never()).save(any());
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
    verify(orders, never()).save(any());
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
    verify(orders, never()).save(any());
  }
}
