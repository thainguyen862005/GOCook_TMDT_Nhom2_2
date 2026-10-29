package com.james.LMS.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.booking.FixedBookingService;
import com.james.LMS.config.PaymentLifecycleProperties;
import com.james.LMS.config.PaymentProperties;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.entity.Product;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.OrderServiceType;
import com.james.LMS.enums.PaymentCloseReason;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.pricing.PricingService;
import com.james.LMS.pricing.PricingService.FixedMenuQuote;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PendingOrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentCheckoutRequest;
import com.james.LMS.request.PaymentOrderCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.response.PaymentIpnResponse;
import com.james.LMS.response.PaymentStatusResponse;
import com.james.LMS.service.PaymentGateway;
import com.james.LMS.service.PaymentGateway.GatewayTransaction;
import com.james.LMS.service.PaymentGateway.PaymentRequest;
import com.james.LMS.service.PaymentService;
import com.james.LMS.voucher.VoucherService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.PageRequest;
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
  private static final String SWEEP_IP = "127.0.0.1";

  private final OrderRepository orders;
  private final PricingService pricing;
  private final FixedBookingService fixedBookingService;
  private final UserRepository users;
  private final PaymentTransactionRepository payments;
  private final PaymentGateway gateway;
  private final PaymentProperties properties;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;
  private final PaymentLifecycleProperties lifecycle;
  private final VoucherService vouchers;
  private final PendingOrderRepository pendingOrders;

  @Override
  public PaymentCheckoutResponse checkout(
      Long customerId, String customerEmail, PaymentCheckoutRequest request, String ipAddress) {
    return transactions.execute(
        status -> {
          Optional<PaymentTransaction> earlier = payments.findByIdempotencyKey(request.idempotencyKey());
          if (earlier.isPresent()) {
            return replay(customerId, earlier.get(), ipAddress);
          }

          FixedMenuQuote quote = pricing.quoteFixedMenu(
              request.productId(), request.guestCount(), request.voucherCode(), customerId);
          Product product = quote.product();

          LocalDateTime now = LocalDateTime.now(PaymentGateway.GATEWAY_ZONE);
          if (!request.scheduledAt().isAfter(now)) {
            throw new PaymentException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.PAYMENT_INVALID_SCHEDULE,
                "Thời gian nấu phải ở tương lai.");
          }

          String orderCode = newOrderCode(now);

          CookOrder order = fixedBookingService.createFixedDraft(
              customerId,
              customerEmail,
              request,
              quote,
              orderCode);

          if (quote.voucher() != null) {
            vouchers.reserve(
                order.getId(),
                customerId,
                quote.subtotal(),
                quote.voucher(),
                OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE));
          }
          return openPayment(
              order, 1, request.idempotencyKey(), ipAddress, now, now.plus(PAYMENT_WINDOW));
        });
  }

  @Override
  public PaymentCheckoutResponse checkoutOrder(
      Long customerId, PaymentOrderCheckoutRequest request, String ipAddress) {
    Long orderId = transactions.execute(status -> ownedOrder(customerId, request.orderCode()).getId());
    settleDueAttempts(orderId, ipAddress);
    transactions.executeWithoutResult(status -> expireOrderIfDue(orderId));

    return transactions.execute(
        status -> {
          Optional<PaymentTransaction> earlier = payments.findByIdempotencyKey(request.idempotencyKey());
          if (earlier.isPresent()) {
            return replay(customerId, earlier.get(), ipAddress);
          }

          CookOrder order = orders.findById(orderId).orElseThrow(this::paymentNotFound);
          List<PaymentTransaction> attempts = payments.findByOrderIdOrderByAttemptNoAsc(orderId);
          Instant now = Instant.now();
          requirePayable(order, attempts, now);

          Optional<PaymentTransaction> open = attempts.stream()
              .filter(attempt -> attempt.getStatus() == PaymentStatus.PENDING)
              .filter(attempt -> attempt.getExpiresAt().toInstant().isAfter(now))
              .findFirst();
          if (open.isPresent()) {
            return replay(customerId, open.get(), ipAddress);
          }

          LocalDateTime localNow = LocalDateTime.ofInstant(now, PaymentGateway.GATEWAY_ZONE);
          LocalDateTime windowEnd = LocalDateTime.ofInstant(payWindowEnd(order), PaymentGateway.GATEWAY_ZONE);
          LocalDateTime expiresAt = localNow.plus(PAYMENT_WINDOW);
          if (windowEnd.isBefore(expiresAt)) {
            expiresAt = windowEnd;
          }
          int attemptNo = attempts.isEmpty() ? 1 : attempts.get(attempts.size() - 1).getAttemptNo() + 1;
          return openPayment(order, attemptNo, request.idempotencyKey(), ipAddress, localNow, expiresAt);
        });
  }

  /**
   * Asks VNPay one last time about every attempt past expiry + grace, then
   * settles or closes it.
   */
  private void settleDueAttempts(Long orderId, String ipAddress) {
    Instant now = Instant.now();
    for (PaymentTransaction attempt : payments.findByOrderIdOrderByAttemptNoAsc(orderId)) {
      if (attempt.getStatus() == PaymentStatus.PENDING && attemptDue(attempt, now)) {
        settleDueAttempt(attempt, ipAddress);
      }
    }
  }

  /**
   * One last querydr for a due attempt: SUCCESS is settled, anything else closes
   * it.
   */
  private boolean settleDueAttempt(PaymentTransaction attempt, String ipAddress) {
    GatewayTransaction answer = gateway.query(
        attempt.getTransactionCode(), readReference(attempt).get("createDate"), ipAddress);
    if (answer.outcome() == PaymentGateway.Outcome.SUCCESS) {
      applyQuietly(answer);
      return true;
    }
    return closeQuietly(attempt.getTransactionCode(), null);
  }

  private CookOrder ownedOrder(Long customerId, String orderCode) {
    return orders
        .findByOrderCode(orderCode.trim())
        .filter(found -> customerId.equals(found.getCustomerId()))
        .orElseThrow(this::paymentNotFound);
  }

  private void requirePayable(CookOrder order, List<PaymentTransaction> attempts, Instant now) {
    String reason = null;
    ErrorCode code = ErrorCode.PAYMENT_ORDER_NOT_PAYABLE;
    if (attempts.stream().anyMatch(attempt -> attempt.getStatus() == PaymentStatus.SUCCESS)) {
      reason = "Đơn đã được thanh toán.";
    } else if (payWindowEnd(order).isBefore(now)) {
      code = ErrorCode.PAYMENT_ORDER_WINDOW_PASSED;
      reason = "Đơn đã quá thời hạn thanh toán.";
    } else if (order.getStatus() != OrderStatus.PENDING) {
      reason = "Đơn không ở trạng thái chờ thanh toán.";
    } else if (order.getChefId() == null) {
      reason = "Đơn chưa có đầu bếp.";
    }
    if (reason != null) {
      throw new PaymentException(HttpStatus.CONFLICT, code, reason);
    }
  }

  private Instant payWindowEnd(CookOrder order) {
    return Instant.ofEpochMilli(order.getCreatedAt()).plus(lifecycle.getOrderPayWindow());
  }

  private PaymentCheckoutResponse openPayment(
      CookOrder order,
      int attemptNo,
      UUID idempotencyKey,
      String ipAddress,
      LocalDateTime now,
      LocalDateTime expiresAt) {
    BigDecimal amount = order.getTotalAmount();
    String txnRef = attemptNo == 1 ? order.getOrderCode() : order.getOrderCode() + "A" + attemptNo;
    payments.save(
        PaymentTransaction.builder()
            .orderId(order.getId())
            .provider(PaymentProvider.VNPAY)
            .transactionCode(txnRef)
            .amount(amount)
            .status(PaymentStatus.PENDING)
            .attemptNo(attemptNo)
            .expiresAt(expiresAt.atZone(PaymentGateway.GATEWAY_ZONE).toOffsetDateTime())
            .rawReference(
                writeReference(
                    Map.of(
                        "createDate", now.format(PaymentGateway.GATEWAY_DATE),
                        "expireDate", expiresAt.format(PaymentGateway.GATEWAY_DATE))))
            .idempotencyKey(idempotencyKey)
            .build());

    String payUrl = payUrl(txnRef, amount, ipAddress, now, expiresAt);
    log.info(
        "payment created order={} attempt={} amount={}",
        order.getOrderCode(),
        attemptNo,
        amount.toPlainString());
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
    String payUrl = payUrl(
        payment.getTransactionCode(),
        payment.getAmount(),
        ipAddress,
        LocalDateTime.parse(reference.get("createDate"), PaymentGateway.GATEWAY_DATE),
        LocalDateTime.parse(reference.get("expireDate"), PaymentGateway.GATEWAY_DATE));
    log.info("payment replayed order={} attempt={}", order.getOrderCode(), payment.getAttemptNo());
    return new PaymentCheckoutResponse(order.getOrderCode(), payUrl);
  }

  private String payUrl(
      String txnRef,
      BigDecimal amount,
      String ipAddress,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    return gateway.createPaymentUrl(
        new PaymentRequest(
            txnRef,
            amount,
            "Thanh toan don hang " + txnRef,
            returnUrl(),
            ipAddress,
            createdAt,
            expiresAt));
  }

  @Override
  public PaymentStatusResponse status(Long customerId, String txnRef, String ipAddress) {
    PaymentTransaction payment = ownedPayment(customerId, txnRef);
    Long orderId = payment.getOrderId();

    if (payment.getStatus() == PaymentStatus.PENDING) {
      Map<String, String> reference = readReference(payment);
      GatewayTransaction answer = gateway.query(txnRef, reference.get("createDate"), ipAddress);

      if (answer.outcome() == PaymentGateway.Outcome.SUCCESS) {
        applyQuietly(answer);
      } else {
        boolean failedForGood = answer.outcome() == PaymentGateway.Outcome.FAILED && attemptDue(payment, Instant.now());
        closeQuietly(txnRef, failedForGood ? answer : null);
      }
    } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
      healEscrowQuietly(payment);
    }
    transactions.executeWithoutResult(status -> expireOrderIfDue(orderId));

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
      case DUPLICATE_SUCCESS:
        return PaymentIpnResponse.confirmed();
      default:
        break;
    }

    PaymentTransaction payment = settled.payment();
    if (payment.getStatus() == PaymentStatus.SUCCESS) {
      if (unsettleable(payment)) {
        return PaymentIpnResponse.alreadyConfirmed();
      }
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

  @Override
  public PaymentStatusResponse handleVnpayReturn(Long customerId, Map<String, String> params) {
    Optional<GatewayTransaction> verified;
    try {
      verified = gateway.verifyCallback(params);
    } catch (IllegalArgumentException e) {
      verified = Optional.empty();
    }
    if (verified.isEmpty()) {
      log.warn("vnpay return rejected: invalid signature");
      throw new PaymentException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.PAYMENT_RETURN_INVALID_SIGNATURE,
          "Dữ liệu trả về từ VNPay không hợp lệ.");
    }

    GatewayTransaction tx = verified.get();
    ownedPayment(customerId, tx.txnRef());
    transactions.executeWithoutResult(
        status -> {
          PaymentTransaction locked = payments
              .lockByProviderAndTransactionCode(PaymentProvider.VNPAY, tx.txnRef())
              .orElseThrow(this::paymentNotFound);
          if (mismatch(locked, tx).isPresent()) {
            throw new PaymentException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.PAYMENT_RETURN_MISMATCH,
                "Dữ liệu trả về từ VNPay không khớp giao dịch.");
          }
          if (tx.outcome() == PaymentGateway.Outcome.CANCELLED) {
            closeIfDead(locked, tx);
          }
        });
    return toStatusResponse(ownedPayment(customerId, tx.txnRef()));
  }

  @Override
  public SweepResult sweepExpired(int max) {
    Instant now = Instant.now();
    OffsetDateTime cutoff = now.minus(lifecycle.getExpiryGrace()).atOffset(java.time.ZoneOffset.UTC);
    int attemptsClosed = 0;
    for (PaymentTransaction due : payments.findDuePending(cutoff, PageRequest.of(0, max))) {
      if (settleDueAttempt(due, SWEEP_IP)) {
        attemptsClosed++;
      }
    }

    int ordersCancelled = 0;
    long windowStart = now.minus(lifecycle.getOrderPayWindow()).toEpochMilli();
    for (Long orderId : pendingOrders.findPendingCreatedBefore(windowStart, PageRequest.of(0, max))) {
      settleDueAttempts(orderId, SWEEP_IP);
      if (Boolean.TRUE.equals(transactions.execute(status -> expireOrderIfDue(orderId)))) {
        ordersCancelled++;
      }
    }
    return new SweepResult(attemptsClosed, ordersCancelled);
  }

  private enum Settlement {
    RECORDED,
    ALREADY_FINAL,
    IGNORED,
    NOT_FOUND,
    FOREIGN_MERCHANT,
    AMOUNT_MISMATCH,
    DUPLICATE_SUCCESS
  }

  private record Settled(Settlement result, PaymentTransaction payment) {
  }

  private static class EscrowFailedException extends RuntimeException {
    EscrowFailedException(Throwable cause) {
      super(cause);
    }
  }

  private Settled recordResult(GatewayTransaction tx) {
    Optional<PaymentTransaction> found = payments.lockByProviderAndTransactionCode(PaymentProvider.VNPAY, tx.txnRef());
    if (found.isEmpty()) {
      return new Settled(Settlement.NOT_FOUND, null);
    }
    PaymentTransaction payment = found.get();

    Optional<Settlement> mismatch = mismatch(payment, tx);
    if (mismatch.isPresent()) {
      return new Settled(mismatch.get(), payment);
    }

    boolean open = payment.getStatus() == PaymentStatus.PENDING;
    boolean closed = payment.getStatus() == PaymentStatus.FAILED
        || payment.getStatus() == PaymentStatus.CANCELLED;
    if (tx.outcome() == PaymentGateway.Outcome.SUCCESS && (open || closed)) {
      return recordSuccess(payment, tx);
    }
    if (!open) {
      return new Settled(Settlement.ALREADY_FINAL, payment);
    }

    switch (tx.outcome()) {
      case FAILED, CANCELLED -> closeIfDead(payment, tx);
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
    return new Settled(Settlement.RECORDED, payment);
  }

  /**
   * The merchant and the amount a gateway callback names must be ours. Used by
   * IPN and return.
   */
  private Optional<Settlement> mismatch(PaymentTransaction payment, GatewayTransaction tx) {
    boolean foreign = tx.merchantCode() != null
        && !tx.merchantCode().isBlank()
        && !tx.merchantCode().equals(gateway.merchantCode());
    if (foreign) {
      return Optional.of(Settlement.FOREIGN_MERCHANT);
    }
    if (tx.amount() != null && tx.amount().compareTo(payment.getAmount()) != 0) {
      return Optional.of(Settlement.AMOUNT_MISMATCH);
    }
    return Optional.empty();
  }

  /**
   * Stores a SUCCESS result, also for an attempt that was already closed: money
   * the customer paid
   * is never dropped. It is settled in escrow only when it is the order's one
   * payment; otherwise
   * it stays stored and findSuccessWithoutEscrow lists it for a refund.
   */
  private Settled recordSuccess(PaymentTransaction payment, GatewayTransaction tx) {
    boolean gatewayTime = tx.payDate() != null && !tx.payDate().isBlank();
    if (!gatewayTime) {
      log.warn("vnpay SUCCESS without pay date order={}; paid_at uses server time", tx.txnRef());
    }
    boolean wasClosed = payment.getStatus() != PaymentStatus.PENDING;
    payment.setStatus(PaymentStatus.SUCCESS);
    payment.setCloseReason(null);
    payment.setPaidAt(gatewayTime ? parsePayDate(tx.payDate()) : OffsetDateTime.now());
    payment.setRawReference(
        mergeReference(payment, gatewayResult(tx, gatewayTime ? "gateway" : "server")));
    payments.save(payment);

    if (unsettleable(payment)) {
      log.error(
          "duplicate payment needs refund order={} payment={}",
          payment.getTransactionCode(),
          payment.getId());
      return new Settled(Settlement.DUPLICATE_SUCCESS, payment);
    }
    if (wasClosed) {
      log.warn("late SUCCESS settles closed attempt order={}", payment.getTransactionCode());
    }
    return new Settled(Settlement.RECORDED, payment);
  }

  /**
   * True when this SUCCESS payment must not be settled: another payment already
   * holds the order's
   * escrow or has the earlier claim, or the order is no longer waiting for
   * payment.
   */
  private boolean unsettleable(PaymentTransaction payment) {
    Optional<Long> held = payments.findEscrowedPaymentId(payment.getOrderId());
    if (held.isPresent()) {
      return !held.get().equals(payment.getId());
    }
    boolean earlierSuccess = payments.findByOrderIdOrderByAttemptNoAsc(payment.getOrderId()).stream()
        .anyMatch(
            other -> other.getStatus() == PaymentStatus.SUCCESS
                && other.getId() < payment.getId());
    CookOrder order = orders.findById(payment.getOrderId()).orElseThrow();
    return earlierSuccess || order.getStatus() != OrderStatus.PENDING;
  }

  private boolean closeQuietly(String txnRef, GatewayTransaction evidence) {
    return Boolean.TRUE.equals(
        transactions.execute(
            status -> payments
                .lockByProviderAndTransactionCode(PaymentProvider.VNPAY, txnRef)
                .map(locked -> closeIfDead(locked, evidence))
                .orElse(false)));
  }

  /**
   * The one place an attempt is closed without SUCCESS. Evidence from the gateway
   * (user cancelled,
   * gateway failed) closes it at once; without evidence it is closed only when
   * its expiry plus the
   * grace has passed. Must run inside a transaction that holds the payment row
   * lock. The order is
   * left alone: the customer may retry until the order pay window ends.
   */
  private boolean closeIfDead(PaymentTransaction payment, GatewayTransaction evidence) {
    if (payment.getStatus() != PaymentStatus.PENDING) {
      return false;
    }
    PaymentStatus finalStatus = null;
    PaymentCloseReason reason = null;
    if (evidence != null) {
      switch (evidence.outcome()) {
        case CANCELLED -> {
          finalStatus = PaymentStatus.CANCELLED;
          reason = PaymentCloseReason.USER_CANCELLED;
        }
        case FAILED -> {
          finalStatus = PaymentStatus.FAILED;
          reason = PaymentCloseReason.GATEWAY_FAILED;
        }
        default -> {
        }
      }
    }
    if (reason == null && attemptDue(payment, Instant.now())) {
      finalStatus = PaymentStatus.CANCELLED;
      reason = PaymentCloseReason.EXPIRED;
    }
    if (reason == null) {
      return false;
    }
    payment.setStatus(finalStatus);
    payment.setCloseReason(reason);
    payments.save(payment);
    log.info(
        "payment attempt closed order={} attempt={} reason={}",
        payment.getTransactionCode(),
        payment.getAttemptNo(),
        reason);
    return true;
  }

  private boolean attemptDue(PaymentTransaction payment, Instant now) {
    if (payment.getExpiresAt() == null) {
      throw new IllegalStateException("Payment " + payment.getId() + " has no expires_at");
    }
    return payment.getExpiresAt().toInstant().plus(lifecycle.getExpiryGrace()).isBefore(now);
  }

  /**
   * Lazy expiry of the order itself: once the pay window has passed with no
   * SUCCESS attempt and no
   * attempt still inside its grace, the open attempts are closed, the order is
   * cancelled and its
   * voucher use is given back. An order that never entered payment (no attempt at
   * all, such as seed
   * data or a booking made from an accepted proposal) is left alone. Run inside a
   * transaction.
   */
  private boolean expireOrderIfDue(Long orderId) {
    CookOrder order = orders.findById(orderId).orElseThrow();
    Instant now = Instant.now();
    if (order.getStatus() != OrderStatus.PENDING || !payWindowEnd(order).isBefore(now)) {
      return false;
    }
    List<PaymentTransaction> attempts = payments.findByOrderIdOrderByAttemptNoAsc(orderId);
    if (attempts.isEmpty()
        || attempts.stream().anyMatch(attempt -> attempt.getStatus() == PaymentStatus.SUCCESS)) {
      return false;
    }
    for (PaymentTransaction attempt : attempts) {
      if (attempt.getStatus() == PaymentStatus.PENDING) {
        if (!attemptDue(attempt, now)) {
          return false;
        }
        closeIfDead(attempt, null);
      }
    }
    if (order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
      order.setStatus(OrderStatus.CANCELLED);
      orders.save(order);
      vouchers.release(orderId);
      log.info("order cancelled after pay window order={}", order.getOrderCode());
      return true;
    }
    return false;
  }

  private boolean registerEscrow(PaymentTransaction payment) {
    try {
      Boolean created = transactions.execute(
          status -> {
            Boolean registered = payments.registerVerifiedPayment(payment.getOrderId(), payment.getId());
            vouchers.consume(payment.getOrderId());
            return registered;
          });
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
    boolean success = settled.payment() != null && settled.payment().getStatus() == PaymentStatus.SUCCESS;
    if (success && settled.result() != Settlement.DUPLICATE_SUCCESS) {
      healEscrowQuietly(settled.payment());
    }
  }

  private void healEscrowQuietly(PaymentTransaction payment) {
    if (unsettleable(payment)) {
      return;
    }
    try {
      registerEscrow(payment);
    } catch (EscrowFailedException e) {
    }
  }

  private String chefName(Product product) {
    return users.findById(product.getSellerId()).map(user -> user.getUsername()).orElse(null);
  }

  private PaymentTransaction ownedPayment(Long customerId, String txnRef) {
    PaymentTransaction payment = payments
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
        payment.getPaidAt(),
        payment.getCloseReason(),
        payment.getAttemptNo());
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
      return mapper.readValue(payment.getRawReference(), new TypeReference<>() {
      });
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
