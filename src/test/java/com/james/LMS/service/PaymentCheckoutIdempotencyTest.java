package com.james.LMS.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.pricing.PricingProperties;
import com.james.LMS.pricing.PricingService;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.entity.Product;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentCheckoutRequest;
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

class PaymentCheckoutIdempotencyTest {
  static final long OWNER = 5L;
  static final long STRANGER = 99L;
  static final long CHEF = 40L;
  static final long ORDER_ID = 3L;

  final OrderRepository orders = mock(OrderRepository.class);
  final ProductRepository products = mock(ProductRepository.class);
  final UserRepository users = mock(UserRepository.class);
  final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
  final PaymentGateway gateway = mock(PaymentGateway.class);
  final PaymentService service =
      new PaymentServiceImpl(
          orders,
          new PricingService(products, new PricingProperties()),
          users,
          payments,
          gateway,
          PaymentServiceTest.properties(),
          new ObjectMapper(),
          new TransactionTemplate(mock(PlatformTransactionManager.class)));

  CookOrder savedOrder;
  PaymentTransaction savedPayment;

  PaymentCheckoutIdempotencyTest() {
    when(products.findById(11L))
        .thenReturn(
            Optional.of(
                Product.builder()
                    .id(11L)
                    .name("Mam com sum vay")
                    .category("Mon Viet")
                    .unitPrice(new BigDecimal("120000.00"))
                    .minGuests(2)
                    .maxGuests(6)
                    .status(ProductStatus.PUBLISHED)
                    .sellerId(CHEF)
                    .build()));
    when(gateway.createPaymentUrl(any()))
        .thenAnswer(call -> "https://pay.example.test/" + call.<PaymentRequest>getArgument(0).txnRef());
    when(orders.save(any(CookOrder.class)))
        .thenAnswer(
            call -> {
              savedOrder = call.getArgument(0);
              ReflectionTestUtils.setField(savedOrder, "id", ORDER_ID);
              return savedOrder;
            });
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
    when(orders.findById(ORDER_ID)).thenAnswer(call -> Optional.ofNullable(savedOrder));
  }

  PaymentCheckoutRequest request(UUID key) {
    return new PaymentCheckoutRequest(
        11L,
        3,
        LocalDateTime.now(PaymentGateway.GATEWAY_ZONE).plusDays(7),
        "Ngoc Anh",
        "0901234567",
        "12 Nguyen Hue, Quan 1",
        null,
        key);
  }

  @Test
  void sameKeyTwiceByTheSameCustomerCreatesOneOrderAndOnePayment() {
    UUID key = UUID.randomUUID();

    PaymentCheckoutResponse first = service.checkout(OWNER, "a@example.test", request(key), "1.2.3.4");
    PaymentCheckoutResponse second = service.checkout(OWNER, "a@example.test", request(key), "1.2.3.4");

    assertThat(second).isEqualTo(first);
    assertThat(savedPayment.getIdempotencyKey()).isEqualTo(key);
    verify(orders, times(1)).save(any(CookOrder.class));
    verify(payments, times(1)).save(any(PaymentTransaction.class));
  }

  @Test
  void replayRebuildsTheSamePaymentRequestFromTheStoredDates() {
    UUID key = UUID.randomUUID();

    service.checkout(OWNER, "a@example.test", request(key), "1.2.3.4");
    service.checkout(OWNER, "a@example.test", request(key), "1.2.3.4");

    ArgumentCaptor<PaymentRequest> sent = ArgumentCaptor.forClass(PaymentRequest.class);
    verify(gateway, times(2)).createPaymentUrl(sent.capture());
    PaymentRequest created = sent.getAllValues().get(0);
    PaymentRequest replayed = sent.getAllValues().get(1);
    assertThat(replayed.txnRef()).isEqualTo(created.txnRef());
    assertThat(replayed.amount()).isEqualByComparingTo(new BigDecimal("360000"));
    assertThat(replayed.createdAt()).isEqualTo(created.createdAt().withNano(0));
    assertThat(replayed.expiresAt()).isEqualTo(created.expiresAt().withNano(0));
  }

  @Test
  void differentKeysCreateSeparateOrders() {
    service.checkout(OWNER, "a@example.test", request(UUID.randomUUID()), "1.2.3.4");
    service.checkout(OWNER, "a@example.test", request(UUID.randomUUID()), "1.2.3.4");

    verify(orders, times(2)).save(any(CookOrder.class));
    verify(payments, times(2)).save(any(PaymentTransaction.class));
  }

  @Test
  void sameKeyFromAnotherCustomerIsRejectedAndCreatesNothing() {
    UUID key = UUID.randomUUID();
    service.checkout(OWNER, "a@example.test", request(key), "1.2.3.4");

    assertThatThrownBy(() -> service.checkout(STRANGER, "b@example.test", request(key), "5.6.7.8"))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT));
    verify(orders, times(1)).save(any(CookOrder.class));
    verify(payments, times(1)).save(any(PaymentTransaction.class));
  }
}
