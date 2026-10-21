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
import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.pricing.PricingProperties;
import com.james.LMS.pricing.PricingService;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentOrderCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.service.PaymentGateway.PaymentRequest;
import com.james.LMS.service.impl.PaymentServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Paying an order that already exists as PENDING, e.g. the booking of an accepted proposal. */
class PaymentOrderCheckoutTest {
  static final long OWNER = 5L;
  static final long STRANGER = 99L;
  static final long ORDER_ID = 8L;
  static final String CODE = "GC261102ABCDE1";

  final OrderRepository orders = mock(OrderRepository.class);
  final UserRepository users = mock(UserRepository.class);
  final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
  final PaymentGateway gateway = mock(PaymentGateway.class);
  final PaymentService service =
      new PaymentServiceImpl(
          orders,
          new PricingService(mock(ProductRepository.class), new PricingProperties()),
          users,
          payments,
          gateway,
          PaymentServiceTest.properties(),
          new ObjectMapper(),
          new TransactionTemplate(mock(PlatformTransactionManager.class)));

  PaymentTransaction savedPayment;

  PaymentOrderCheckoutTest() {
    when(payments.save(any(PaymentTransaction.class)))
        .thenAnswer(
            call -> {
              savedPayment = call.getArgument(0);
              return savedPayment;
            });
    when(payments.findByIdempotencyKey(any()))
        .thenAnswer(
            call ->
                Optional.ofNullable(savedPayment)
                    .filter(p -> call.getArgument(0).equals(p.getIdempotencyKey())));
    when(gateway.createPaymentUrl(any()))
        .thenAnswer(call -> "https://pay.example.test/" + call.<PaymentRequest>getArgument(0).txnRef());
  }

  CookOrder order(OrderStatus status, Long chefId, Long customerId) {
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
            .build();
    ReflectionTestUtils.setField(order, "id", ORDER_ID);
    return order;
  }

  void existing(CookOrder order) {
    when(orders.findByOrderCode(CODE)).thenReturn(Optional.of(order));
    when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
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
  void paymentIsOpenedForTheExistingOrderWithTheOrderTotal() {
    CookOrder order = order(OrderStatus.PENDING, 40L, OWNER);
    existing(order);
    UUID key = UUID.randomUUID();

    PaymentCheckoutResponse response = service.checkoutOrder(OWNER, request(key), "1.2.3.4");

    assertThat(response.orderCode()).isEqualTo(CODE);
    assertThat(response.payUrl()).isEqualTo("https://pay.example.test/" + CODE);
    assertThat(savedPayment.getOrderId()).isEqualTo(ORDER_ID);
    assertThat(savedPayment.getProvider()).isEqualTo(PaymentProvider.VNPAY);
    assertThat(savedPayment.getTransactionCode()).isEqualTo(CODE);
    assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(savedPayment.getAmount()).isEqualByComparingTo("1200000");
    assertThat(savedPayment.getIdempotencyKey()).isEqualTo(key);
    ArgumentCaptor<PaymentRequest> sent = ArgumentCaptor.forClass(PaymentRequest.class);
    verify(gateway).createPaymentUrl(sent.capture());
    assertThat(sent.getValue().amount()).isEqualByComparingTo("1200000");
    assertThat(sent.getValue().txnRef()).isEqualTo(CODE);
    verify(orders, never()).save(any());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void sameKeyTwiceByTheOwnerOpensOnePayment() {
    existing(order(OrderStatus.PENDING, 40L, OWNER));
    UUID key = UUID.randomUUID();

    PaymentCheckoutResponse first = service.checkoutOrder(OWNER, request(key), "1.2.3.4");
    PaymentCheckoutResponse second = service.checkoutOrder(OWNER, request(key), "1.2.3.4");

    assertThat(second).isEqualTo(first);
    verify(payments, times(1)).save(any(PaymentTransaction.class));
  }

  @Test
  void sameKeyFromAnotherCustomerIsRejected() {
    existing(order(OrderStatus.PENDING, 40L, OWNER));
    UUID key = UUID.randomUUID();
    service.checkoutOrder(OWNER, request(key), "1.2.3.4");

    assertRejected(
        () -> service.checkoutOrder(STRANGER, request(key), "1.2.3.4"),
        ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
    verify(payments, times(1)).save(any(PaymentTransaction.class));
  }

  @Test
  void anotherCustomersOrAnUnknownOrderLooksLikeNotFound() {
    existing(order(OrderStatus.PENDING, 40L, OWNER));

    assertRejected(
        () -> service.checkoutOrder(STRANGER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_NOT_FOUND);
    assertRejected(
        () -> service.checkoutOrder(OWNER, new PaymentOrderCheckoutRequest("GC000000NOPE00", UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_NOT_FOUND);
    verify(payments, never()).save(any());
  }

  @Test
  void onlyAPendingOrderWithAChefCanBePaid() {
    for (OrderStatus status : new OrderStatus[] {OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.COMPLETED}) {
      existing(order(status, 40L, OWNER));
      assertRejected(
          () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
          ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    }
    existing(order(OrderStatus.PENDING, null, OWNER));
    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    verify(payments, never()).save(any());
  }

  @Test
  void anOrderThatAlreadyHasAPaymentCannotOpenAnother() {
    existing(order(OrderStatus.PENDING, 40L, OWNER));
    when(payments.findByProviderAndTransactionCode(PaymentProvider.VNPAY, CODE))
        .thenReturn(Optional.of(PaymentTransaction.builder().orderId(ORDER_ID).build()));

    assertRejected(
        () -> service.checkoutOrder(OWNER, request(UUID.randomUUID()), "ip"),
        ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    verify(payments, never()).save(any());
  }
}
