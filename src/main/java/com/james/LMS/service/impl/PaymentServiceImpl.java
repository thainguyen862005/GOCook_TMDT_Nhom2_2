package com.james.LMS.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.config.PaymentProperties;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.entity.Product;
import com.james.LMS.pricing.PricingService;
import com.james.LMS.pricing.PricingService.FixedMenuQuote;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentCheckoutRequest;
import com.james.LMS.request.PaymentOrderCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.response.PaymentIpnResponse;
import com.james.LMS.response.PaymentStatusResponse;
import com.james.LMS.service.PaymentGateway;
import com.james.LMS.service.PaymentGateway.GatewayTransaction;
import com.james.LMS.service.PaymentGateway.Outcome;
import com.james.LMS.service.PaymentGateway.PaymentRequest;
import com.james.LMS.service.PaymentService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

  private static final String RESULT_PATH = "/payment-result";

  private static final String ORDER_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int ORDER_CODE_RANDOM_LENGTH = 6;
  private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
  private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OrderRepository orders;
  private final PricingService pricing;
  private final UserRepository users;
  private final PaymentTransactionRepository payments;
  private final PaymentGateway gateway;
  private final PaymentProperties properties;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;

  @Override
  public PaymentCheckoutResponse checkout(
      Long customerId, String customerEmail, PaymentCheckoutRequest request, String ipAddress) {
    return transactions.execute(
        status -> {
          Optional<PaymentTransaction> earlier = payments.findByIdempotencyKey(request.idempotencyKey());
          if (earlier.isPresent()) {
            return replay(customerId, earlier.get(), ipAddress);
          }

          FixedMenuQuote quote = pricing.quoteFixedMenu(request.productId(), request.guestCount());
          Product product = quote.product();

          LocalDateTime now = LocalDateTime.now(PaymentGateway.GATEWAY_ZONE);
          if (!request.scheduledAt().isAfter(now)) {
            throw new PaymentException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.PAYMENT_INVALID_SCHEDULE,
                "Thời gian nấu phải ở tương lai.");
          }

          String orderCode = newOrderCode(now);

          CookOrder order =
              orders.save(
                  CookOrder.builder()
                      .orderCode(orderCode)
                      .customerName(request.customerName().trim())
                      .customerEmail(customerEmail)
                      .customerPhone(request.customerPhone().trim())
                      .customerId(customerId)
                      .productName(product.getName())
                      .chefName(chefName(product))
                      .chefId(product.getSellerId())
                      .serviceAddress(request.serviceAddress().trim())
                      .guestCount(request.guestCount())
                      .scheduledAt(request.scheduledAt())
                      .totalAmount(quote.total())
                      .status(OrderStatus.PENDING)
                      .customerNote(request.customerNote())
                      .build());

          return openPayment(order, request.idempotencyKey(), ipAddress, now);
        });
  }

  @Override
  public PaymentCheckoutResponse checkoutOrder(
      Long customerId, PaymentOrderCheckoutRequest request, String ipAddress) {
    return transactions.execute(
        status -> {
          Optional<PaymentTransaction> earlier = payments.findByIdempotencyKey(request.idempotencyKey());
          if (earlier.isPresent()) {
            return replay(customerId, earlier.get(), ipAddress);
          }

          CookOrder order =
              orders
                  .findByOrderCode(request.orderCode().trim())
                  .filter(found -> customerId.equals(found.getCustomerId()))
                  .orElseThrow(this::paymentNotFound);
          requirePayable(order);
          return openPayment(
              order,
              request.idempotencyKey(),
              ipAddress,
              LocalDateTime.now(PaymentGateway.GATEWAY_ZONE));
        });
  }

  private void requirePayable(CookOrder order) {
    String reason = null;
    if (order.getStatus() != OrderStatus.PENDING) {
      reason = "Đơn không ở trạng thái chờ thanh toán.";
    } else if (order.getChefId() == null) {
      reason = "Đơn chưa có đầu bếp.";
    } else if (payments
        .findByProviderAndTransactionCode(PaymentProvider.VNPAY, order.getOrderCode())
        .isPresent()) {
      reason = "Đơn đã có giao dịch thanh toán.";
    }
    if (reason != null) {
      throw new PaymentException(HttpStatus.CONFLICT, ErrorCode.PAYMENT_ORDER_NOT_PAYABLE, reason);
    }
  }

  private PaymentCheckoutResponse openPayment(
      CookOrder order, UUID idempotencyKey, String ipAddress, LocalDateTime now) {
    LocalDateTime expiresAt = now.plus(PAYMENT_WINDOW);
    BigDecimal amount = order.getTotalAmount();
    payments.save(
        PaymentTransaction.builder()
            .orderId(order.getId())
            .provider(PaymentProvider.VNPAY)
            .transactionCode(order.getOrderCode())
            .amount(amount)
            .status(PaymentStatus.PENDING)
            .rawReference(
                writeReference(
                    Map.of(
                        "createDate", now.format(PaymentGateway.GATEWAY_DATE),
                        "expireDate", expiresAt.format(PaymentGateway.GATEWAY_DATE))))
            .idempotencyKey(idempotencyKey)
            .build());

    String payUrl = payUrl(order.getOrderCode(), amount, ipAddress, now, expiresAt);
    log.info("payment created order={} amount={}", order.getOrderCode(), amount.toPlainString());
    return new PaymentCheckoutResponse(order.getOrderCode(), payUrl);
  }

  private PaymentCheckoutResponse replay(
      Long customerId, PaymentTransaction payment, String ipAddress) {
    CookOrder order = orders.findById(payment.getOrderId()).orElseThrow();
    if (!customerId.equals(order.getCustomerId())) {
      throw new PaymentException(
          HttpStatus.CONFLICT,
          ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT,
          "Khóa chống gửi trùng đã được dùng cho giao dịch khác.");
    }
    Map<String, String> reference = readReference(payment);
    String orderCode = payment.getTransactionCode();
    String payUrl =
        payUrl(
            orderCode,
            payment.getAmount(),
            ipAddress,
            LocalDateTime.parse(reference.get("createDate"), PaymentGateway.GATEWAY_DATE),
            LocalDateTime.parse(reference.get("expireDate"), PaymentGateway.GATEWAY_DATE));
    log.info("payment replayed order={}", orderCode);
    return new PaymentCheckoutResponse(orderCode, payUrl);
  }

  private String payUrl(
      String orderCode,
      BigDecimal amount,
      String ipAddress,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    return gateway.createPaymentUrl(
        new PaymentRequest(
            orderCode,
            amount,
            "Thanh toan don hang " + orderCode,
            returnUrl(),
            ipAddress,
            createdAt,
            expiresAt));
  }

  @Override
  public PaymentStatusResponse status(Long customerId, String txnRef, String ipAddress) {
    PaymentTransaction payment = ownedPayment(customerId, txnRef);

    if (payment.getStatus() == PaymentStatus.PENDING) {
      Map<String, String> reference = readReference(payment);
      boolean expired = isExpired(reference);
      GatewayTransaction answer =
          gateway.query(txnRef, reference.get("createDate"), ipAddress);

      Optional<GatewayTransaction> decisive =
          switch (answer.outcome()) {
            case SUCCESS -> Optional.of(answer);
            case FAILED -> expired ? Optional.of(answer) : Optional.empty();
            case NOT_FOUND ->
                expired ? Optional.of(answer.withOutcome(Outcome.CANCELLED)) : Optional.empty();
            default -> Optional.empty();
          };
      decisive.ifPresent(this::applyQuietly);
    } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
      healEscrowQuietly(payment);
    }

    return toStatusResponse(ownedPayment(customerId, txnRef));
  }

  @Override
  public PaymentIpnResponse handleVnpayIpn(Map<String, String> params) {
    Optional<GatewayTransaction> verified;
    try {
      verified = gateway.verifyCallback(params);
    } catch (IllegalArgumentException e) {
      log.warn("vnpay ipn rejected: unreadable parameters");
      return PaymentIpnResponse.unknownError();
    }
    if (verified.isEmpty()) {
      log.warn("vnpay ipn rejected: invalid signature");
      return PaymentIpnResponse.invalidChecksum();
    }

    GatewayTransaction tx = verified.get();
    Settled settled = transactions.execute(status -> recordResult(tx));
    log.info(
        "vnpay ipn order={} gatewayOutcome={} result={}",
        tx.txnRef(),
        tx.outcome(),
        settled.result());

    switch (settled.result()) {
      case NOT_FOUND, FOREIGN_MERCHANT:
        return PaymentIpnResponse.orderNotFound();
      case AMOUNT_MISMATCH:
        return PaymentIpnResponse.invalidAmount();
      default:
        break;
    }

    PaymentTransaction payment = settled.payment();
    if (payment.getStatus() == PaymentStatus.SUCCESS) {
      boolean escrowCreated;
      try {
        escrowCreated = registerEscrow(payment);
      } catch (EscrowFailedException e) {
        return PaymentIpnResponse.unknownError();
      }
      boolean changed = settled.result() == Settlement.RECORDED;
      return changed || escrowCreated
          ? PaymentIpnResponse.confirmed()
          : PaymentIpnResponse.alreadyConfirmed();
    }
    return settled.result() == Settlement.ALREADY_FINAL
        ? PaymentIpnResponse.alreadyConfirmed()
        : PaymentIpnResponse.confirmed();
  }

  private enum Settlement {
    RECORDED,
    ALREADY_FINAL,
    IGNORED,
    NOT_FOUND,
    FOREIGN_MERCHANT,
    AMOUNT_MISMATCH
  }

  private record Settled(Settlement result, PaymentTransaction payment) {}

  private static class EscrowFailedException extends RuntimeException {
    EscrowFailedException(Throwable cause) {
      super(cause);
    }
  }

  private Settled recordResult(GatewayTransaction tx) {
    Optional<PaymentTransaction> found =
        payments.lockByProviderAndTransactionCode(PaymentProvider.VNPAY, tx.txnRef());
    if (found.isEmpty()) {
      return new Settled(Settlement.NOT_FOUND, null);
    }
    PaymentTransaction payment = found.get();

    boolean foreign =
        tx.merchantCode() != null
            && !tx.merchantCode().isBlank()
            && !tx.merchantCode().equals(gateway.merchantCode());
    if (foreign) {
      return new Settled(Settlement.FOREIGN_MERCHANT, payment);
    }
    if (tx.amount() != null && tx.amount().compareTo(payment.getAmount()) != 0) {
      return new Settled(Settlement.AMOUNT_MISMATCH, payment);
    }
    if (payment.getStatus() != PaymentStatus.PENDING) {
      return new Settled(Settlement.ALREADY_FINAL, payment);
    }

    switch (tx.outcome()) {
      case SUCCESS -> {
        boolean gatewayTime = tx.payDate() != null && !tx.payDate().isBlank();
        if (!gatewayTime) {
          log.warn("vnpay SUCCESS without pay date order={}; paid_at uses server time", tx.txnRef());
        }
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(gatewayTime ? parsePayDate(tx.payDate()) : OffsetDateTime.now());
        payment.setRawReference(
            mergeReference(payment, gatewayResult(tx, gatewayTime ? "gateway" : "server")));
      }
      case FAILED -> closeUnpaid(payment, PaymentStatus.FAILED);
      case CANCELLED -> closeUnpaid(payment, PaymentStatus.CANCELLED);
      default -> {
        log.warn(
            "vnpay result not decisive order={} outcome={} code={} status={}",
            tx.txnRef(),
            tx.outcome(),
            tx.responseCode(),
            tx.transactionStatus());
        payment.setRawReference(mergeReference(payment, gatewayResult(tx, null)));
        payments.save(payment);
        return new Settled(Settlement.IGNORED, payment);
      }
    }
    payments.save(payment);
    return new Settled(Settlement.RECORDED, payment);
  }

  private void closeUnpaid(PaymentTransaction payment, PaymentStatus finalStatus) {
    payment.setStatus(finalStatus);
    CookOrder order = orders.findById(payment.getOrderId()).orElseThrow();
    if (order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
      order.setStatus(OrderStatus.CANCELLED);
      orders.save(order);
    } else {
      log.warn(
          "order {} stays {} after payment {}",
          order.getOrderCode(),
          order.getStatus(),
          finalStatus);
    }
  }

  private boolean registerEscrow(PaymentTransaction payment) {
    try {
      Boolean created =
          transactions.execute(
              status -> payments.registerVerifiedPayment(payment.getOrderId(), payment.getId()));
      return Boolean.TRUE.equals(created);
    } catch (RuntimeException e) {
      String detail = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
      log.error(
          "payment SUCCESS but escrow failed order={} reason={} detail={}",
          payment.getTransactionCode(),
          escrowFailureReason(detail),
          detail.lines().findFirst().orElse(""));
      throw new EscrowFailedException(e);
    }
  }

  private static String escrowFailureReason(String detail) {
    if (detail.contains("must be PENDING")) {
      return "ORDER_NOT_PENDING";
    }
    if (detail.contains("Missing verified")) {
      return "PAYMENT_NOT_VERIFIED";
    }
    if (detail.contains("Assign chef_id")) {
      return "ORDER_PARTIES_MISSING";
    }
    if (detail.contains("Payment mismatch")) {
      return "AMOUNT_MISMATCH";
    }
    return "UNEXPECTED_DB_ERROR";
  }

  private void applyQuietly(GatewayTransaction tx) {
    Settled settled = transactions.execute(status -> recordResult(tx));
    log.info("payment pull order={} outcome={} result={}", tx.txnRef(), tx.outcome(), settled.result());
    if (settled.payment() != null && settled.payment().getStatus() == PaymentStatus.SUCCESS) {
      healEscrowQuietly(settled.payment());
    }
  }

  private void healEscrowQuietly(PaymentTransaction payment) {
    try {
      registerEscrow(payment);
    } catch (EscrowFailedException e) {
    }
  }

  private String chefName(Product product) {
    return users.findById(product.getSellerId()).map(user -> user.getUsername()).orElse(null);
  }

  private PaymentTransaction ownedPayment(Long customerId, String txnRef) {
    PaymentTransaction payment =
        payments
            .findByProviderAndTransactionCode(PaymentProvider.VNPAY, txnRef)
            .orElseThrow(this::paymentNotFound);
    CookOrder order = orders.findById(payment.getOrderId()).orElseThrow(this::paymentNotFound);
    if (!customerId.equals(order.getCustomerId())) {
      throw paymentNotFound();
    }
    return payment;
  }

  private PaymentException paymentNotFound() {
    return new PaymentException(
        HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND, "Không tìm thấy giao dịch thanh toán.");
  }

  private PaymentStatusResponse toStatusResponse(PaymentTransaction payment) {
    CookOrder order = orders.findById(payment.getOrderId()).orElseThrow();
    return new PaymentStatusResponse(
        order.getOrderCode(),
        payment.getStatus(),
        order.getStatus(),
        order.getProductName(),
        order.getChefName(),
        order.getGuestCount(),
        order.getScheduledAt(),
        payment.getAmount(),
        payment.getPaidAt());
  }

  private String returnUrl() {
    String base = properties.getFrontendBaseUrl();
    return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + RESULT_PATH;
  }

  private static String newOrderCode(LocalDateTime now) {
    StringBuilder code = new StringBuilder("GC").append(now.format(ORDER_CODE_DATE));
    for (int i = 0; i < ORDER_CODE_RANDOM_LENGTH; i++) {
      code.append(ORDER_CODE_ALPHABET.charAt(RANDOM.nextInt(ORDER_CODE_ALPHABET.length())));
    }
    return code.toString();
  }

  private static OffsetDateTime parsePayDate(String payDate) {
    return LocalDateTime.parse(payDate, PaymentGateway.GATEWAY_DATE)
        .atZone(PaymentGateway.GATEWAY_ZONE)
        .toOffsetDateTime();
  }

  private static Map<String, String> gatewayResult(GatewayTransaction tx, String paidAtSource) {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("transactionNo", tx.transactionNo());
    result.put("responseCode", tx.responseCode());
    result.put("transactionStatus", tx.transactionStatus());
    result.put("paidAtSource", paidAtSource);
    return result;
  }

  private boolean isExpired(Map<String, String> reference) {
    LocalDateTime expireDate =
        LocalDateTime.parse(reference.get("expireDate"), PaymentGateway.GATEWAY_DATE);
    return expireDate.isBefore(LocalDateTime.now(PaymentGateway.GATEWAY_ZONE));
  }

  private String mergeReference(PaymentTransaction payment, Map<String, String> additions) {
    Map<String, String> reference = new LinkedHashMap<>(readReference(payment));
    additions.forEach(
        (key, value) -> {
          if (value != null && !value.isBlank()) {
            reference.put(key, value);
          }
        });
    return writeReference(reference);
  }

  private Map<String, String> readReference(PaymentTransaction payment) {
    try {
      return mapper.readValue(payment.getRawReference(), new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unreadable payment reference", e);
    }
  }

  private String writeReference(Map<String, String> reference) {
    try {
      return mapper.writeValueAsString(reference);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unwritable payment reference", e);
    }
  }
}
