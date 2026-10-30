package com.james.LMS.refund;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.refund.RefundGateway.RefundOutcome;
import com.james.LMS.refund.RefundGateway.RefundRequest;
import com.james.LMS.refund.RefundPolicy.Decision;
import com.james.LMS.refund.RefundStore.AdminRequestRow;
import com.james.LMS.refund.RefundStore.EscrowRow;
import com.james.LMS.refund.RefundStore.NewRefund;
import com.james.LMS.refund.RefundStore.PaymentRow;
import com.james.LMS.voucher.VoucherService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives money back after a cancellation. Every public method is one transaction that locks the
 * order, then the escrow, then the payment. It never changes the order status: the order module
 * does that first, then calls {@link #requestRefund}.
 *
 * <p>Escrow after a refund: HELD becomes REFUND_PENDING while the refund is open, REFUNDED when the
 * gateway paid it (also for a partial refund, the retained part is only recorded), and stays
 * REFUND_PENDING when the gateway failed so an admin can {@link #retry}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

  private final RefundStore store;
  private final RefundPolicy policy;
  private final RefundGateway gateway;
  private final VoucherService vouchers;
  private final TransactionTemplate transactions;

  /**
   * Refund for a cancelled or rejected order. Empty when nothing was ever paid. A repeated call
   * returns the refund that already exists, whatever its status.
   */
  public Optional<Refund> requestRefund(CancellationContext context) {
    return Optional.ofNullable(transactions.execute(status -> request(context)));
  }

  private Refund request(CancellationContext context) {
    Long orderId = context.orderId();
    store.lockOrder(orderId).orElseThrow(() -> orderNotFound());
    Optional<EscrowRow> found = store.lockEscrow(orderId);
    if (found.isEmpty()) {
      if (store.hasSuccessPayment(orderId)) {
        throw fail(
            HttpStatus.CONFLICT,
            ErrorCode.REFUND_NO_ESCROW,
            "Đơn có thanh toán thành công nhưng chưa có khoản giữ tiền để hoàn.");
      }
      return null;
    }
    EscrowRow escrow = found.get();
    PaymentRow payment = store.lockPayment(escrow.paymentId()).orElseThrow();

    Optional<Refund> existing = store.latestForPayment(payment.id());
    if (existing.isPresent()) {
      return existing.get();
    }
    requireRefundable(escrow);

    Decision decision = policy.decide(context, escrow.gross());
    Refund refund =
        store.insert(
            new NewRefund(
                orderId,
                payment.id(),
                decision.amount(),
                decision.retained(),
                decision.percent(),
                reasonOf(context.cancelledBy()),
                null));
    return settle(refund, escrow, payment, context.reason());
  }

  /**
   * Automatically opens the refund case after an order cancellation/rejection, WITHOUT
   * calling a payment provider. The current gateway is simulated; reporting REFUNDED
   * automatically would be a financial misstatement. An authorized payout worker must
   * execute a real, reconciled gateway transfer later.
   *
   * Transaction lock order: order -> escrow -> payment. Duplicate calls reuse the refund.
   */
  public Optional<Refund> queueRefund(CancellationContext context) {
    return Optional.ofNullable(transactions.execute(status -> {
      store.lockOrder(context.orderId()).orElseThrow(() -> orderNotFound());
      Optional<EscrowRow> escrowFound = store.lockEscrow(context.orderId());
      if (escrowFound.isEmpty()) {
        if (store.hasSuccessPayment(context.orderId()))
          throw fail(HttpStatus.CONFLICT, ErrorCode.REFUND_NO_ESCROW,
              "Thanh toán thành công nhưng escrow chưa đồng bộ; cần Admin đối soát.");
        return null; // Never paid: no refund to request.
      }
      EscrowRow escrow = escrowFound.get();
      PaymentRow payment = store.lockPayment(escrow.paymentId()).orElseThrow();
      Optional<Refund> existing = store.latestForPayment(payment.id());
      if (existing.isPresent()) return existing.get();
      requireRefundable(escrow);
      Decision decision = policy.decide(context, escrow.gross());
      Refund created = store.insert(new NewRefund(context.orderId(), payment.id(), decision.amount(),
          decision.retained(), decision.percent(), reasonOf(context.cancelledBy()), null));
      if ("HELD".equals(escrow.status())) store.holdForRefund(escrow, "Auto queued: " + context.reason());
      log.info("refund requested automatically order={} refund={} amount={}",
          context.orderId(),created.id(),created.amount());
      return created;
    }));
  }

  /** Safe automatic detection for a second SUCCESS payment when another payment is
   * already the authoritative escrow payment. No outward money transfer is attempted. */
  public Refund queueDuplicate(Long paymentId) {
    Long orderId = store.paymentOrderId(paymentId)
        .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND, "Không tìm thấy giao dịch."));
    return transactions.execute(status -> {
      store.lockOrder(orderId).orElseThrow(() -> orderNotFound());
      PaymentRow payment = store.lockPayment(paymentId).orElseThrow();
      var old = store.latestForPayment(paymentId);
      if (old.isPresent()) return old.get();
      var heldId = store.escrowedPaymentId(orderId);
      if (!"SUCCESS".equals(payment.status()) || heldId.isEmpty() || heldId.get().equals(paymentId))
        throw fail(HttpStatus.CONFLICT, ErrorCode.REFUND_NOT_DUPLICATE, "Không đủ bằng chứng thanh toán trùng.");
      return store.insert(new NewRefund(orderId,paymentId,payment.amount(),BigDecimal.ZERO,
          100,RefundReason.DUPLICATE_PAYMENT,null));
    });
  }

  /** Runs the refund again after a gateway failure. */
  public Refund retry(Long refundId) {
    return transactions.execute(
        status -> {
          Refund peeked =
              store
                  .peek(refundId)
                  .orElseThrow(
                      () -> fail(HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND, "Không tìm thấy hoàn tiền."));
          store.lockOrder(peeked.orderId()).orElseThrow(() -> orderNotFound());
          Refund refund = store.lockRefund(refundId).orElseThrow();
          if (refund.status() != RefundStatus.FAILED) {
            throw fail(
                HttpStatus.CONFLICT,
                ErrorCode.REFUND_NOT_RETRYABLE,
                "Chỉ hoàn tiền thất bại mới được thử lại.");
          }
          return resume(refund);
        });
  }

  /**
   * Refunds what an admin approved (admin_refund_requests in status REVIEWED). Repeating it for a
   * request that was already executed returns the same refund.
   */
  public Refund executeApproved(Long adminRequestId) {
    return transactions.execute(
        status -> {
          AdminRequestRow request =
              store
                  .lockAdminRequest(adminRequestId)
                  .orElseThrow(() -> adminRequestInvalid("Không tìm thấy yêu cầu hoàn tiền."));
          Optional<Refund> prior = store.byAdminRequest(adminRequestId);
          if (prior.isPresent()) {
            return resumeAdmin(prior.get());
          }
          if (!"REVIEWED".equals(request.status())) {
            throw adminRequestInvalid("Chỉ yêu cầu đã duyệt (REVIEWED) mới được thực hiện.");
          }

          Long orderId = request.orderId();
          store.lockOrder(orderId).orElseThrow(() -> orderNotFound());
          EscrowRow escrow =
              store
                  .lockEscrow(orderId)
                  .orElseThrow(
                      () ->
                          fail(
                              HttpStatus.CONFLICT,
                              ErrorCode.REFUND_NO_ESCROW,
                              "Đơn không có khoản giữ tiền để hoàn."));
          PaymentRow payment = store.lockPayment(escrow.paymentId()).orElseThrow();
          if (store.latestForPayment(payment.id()).isPresent()) {
            throw fail(
                HttpStatus.CONFLICT,
                ErrorCode.REFUND_ALREADY_EXISTS,
                "Giao dịch này đã có hoàn tiền.");
          }
          requireRefundable(escrow);

          BigDecimal amount = request.amount().setScale(0, RoundingMode.DOWN);
          if (amount.signum() <= 0) {
            throw adminRequestInvalid("Số tiền hoàn phải từ 1 đồng.");
          }
          if (amount.compareTo(escrow.gross()) > 0) {
            throw fail(
                HttpStatus.CONFLICT,
                ErrorCode.REFUND_AMOUNT_EXCEEDS,
                "Số tiền hoàn vượt số tiền khách đã trả.");
          }
          int percent =
              amount.multiply(BigDecimal.valueOf(100)).divide(escrow.gross(), 0, RoundingMode.DOWN).intValue();
          Refund refund =
              store.insert(
                  new NewRefund(
                      orderId,
                      payment.id(),
                      amount,
                      escrow.gross().subtract(amount),
                      percent,
                      RefundReason.ADMIN_APPROVED,
                      adminRequestId));
          Refund done = settle(refund, escrow, payment, "Admin approved refund request " + adminRequestId);
          markAdminIfRefunded(done);
          return done;
        });
  }

  /** Full refund of a SUCCESS payment that is not the one held in escrow. No escrow is touched. */
  public Refund refundDuplicate(Long paymentId) {
    Long orderId =
        store
            .paymentOrderId(paymentId)
            .orElseThrow(
                () -> fail(HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND, "Không tìm thấy giao dịch."));
    return transactions.execute(
        status -> {
          store.lockOrder(orderId).orElseThrow(() -> orderNotFound());
          PaymentRow payment = store.lockPayment(paymentId).orElseThrow();
          Optional<Refund> existing = store.latestForPayment(paymentId);
          if (existing.isPresent()) {
            return existing.get();
          }
          if (!"SUCCESS".equals(payment.status()) || !isDuplicate(payment)) {
            throw fail(
                HttpStatus.CONFLICT,
                ErrorCode.REFUND_NOT_DUPLICATE,
                "Giao dịch này không phải thanh toán trùng.");
          }
          Refund refund =
              store.insert(
                  new NewRefund(
                      orderId,
                      paymentId,
                      payment.amount(),
                      BigDecimal.ZERO,
                      100,
                      RefundReason.DUPLICATE_PAYMENT,
                      null));
          return settle(refund, null, payment, "Duplicate payment " + paymentId);
        });
  }

  private boolean isDuplicate(PaymentRow payment) {
    Optional<Long> held = store.escrowedPaymentId(payment.orderId());
    if (held.isPresent()) {
      return !held.get().equals(payment.id());
    }
    return !"PENDING".equals(store.orderStatus(payment.orderId()));
  }

  /** Continues a FAILED refund; the order is already locked and the refund row is locked too. */
  private Refund resume(Refund refund) {
    Optional<EscrowRow> escrow =
        store.lockEscrow(refund.orderId()).filter(e -> e.paymentId().equals(refund.paymentTransactionId()));
    PaymentRow payment = store.lockPayment(refund.paymentTransactionId()).orElseThrow();
    store.markRequested(refund.id());
    Refund done = settle(store.find(refund.id()), escrow.orElse(null), payment, "Retry refund " + refund.id());
    markAdminIfRefunded(done);
    return done;
  }

  private Refund resumeAdmin(Refund prior) {
    store.lockOrder(prior.orderId()).orElseThrow(() -> orderNotFound());
    Refund locked = store.lockRefund(prior.id()).orElseThrow();
    if (locked.status() == RefundStatus.FAILED) {
      return resume(locked);
    }
    markAdminIfRefunded(locked);
    return locked;
  }

  private void markAdminIfRefunded(Refund refund) {
    if (refund.status() == RefundStatus.REFUNDED && refund.adminRefundRequestId() != null) {
      store.markAdminExecuted(refund.adminRefundRequestId(), refund.gatewayReference());
    }
  }

  /**
   * Sends the money back and records the result. The escrow (when there is one) leaves HELD for
   * REFUND_PENDING first, so a gateway failure leaves it waiting there.
   */
  private Refund settle(Refund refund, EscrowRow escrow, PaymentRow payment, String note) {
    if (escrow != null && "HELD".equals(escrow.status())) {
      store.holdForRefund(escrow, note);
    }

    RefundOutcome outcome =
        refund.amount().signum() == 0
            ? RefundOutcome.success(null)
            : gateway.refund(
                new RefundRequest(refund.id(), payment.id(), refund.amount(), refund.reasonCode()));
    if (!outcome.success()) {
      store.markFailed(refund.id(), truncate(outcome.message()));
      log.warn("refund failed refund={} payment={}", refund.id(), payment.id());
      return store.find(refund.id());
    }

    store.markRefunded(refund.id(), outcome.reference());
    if (escrow != null) {
      store.escrowRefunded(escrow, refund.amount(), note + "; retained " + refund.retainedAmount().toPlainString());
    }
    if (refund.amount().compareTo(payment.amount()) == 0) {
      store.paymentRefunded(payment.id());
      if (escrow != null) {
        vouchers.returnAfterRefund(refund.orderId());
      }
    }
    log.info("refund done refund={} order={} amount={}", refund.id(), refund.orderId(), refund.amount().toPlainString());
    return store.find(refund.id());
  }

  private static void requireRefundable(EscrowRow escrow) {
    if ("RELEASED".equals(escrow.status())) {
      throw fail(
          HttpStatus.CONFLICT,
          ErrorCode.REFUND_ESCROW_RELEASED,
          "Tiền đã giải ngân cho đầu bếp, không thể hoàn.");
    }
    if (!"HELD".equals(escrow.status()) && !"REFUND_PENDING".equals(escrow.status())) {
      throw new IllegalStateException(
          "Escrow " + escrow.id() + " is " + escrow.status() + " but has no refund");
    }
  }

  private static RefundReason reasonOf(CancelledBy by) {
    return switch (by) {
      case CHEF -> RefundReason.CHEF_REJECTED;
      case CUSTOMER -> RefundReason.CUSTOMER_CANCEL;
      case ADMIN -> RefundReason.ADMIN_APPROVED;
    };
  }

  private static String truncate(String message) {
    String text = message == null ? "Hoàn tiền thất bại" : message;
    return text.length() > 1000 ? text.substring(0, 1000) : text;
  }

  private static PaymentException orderNotFound() {
    return fail(HttpStatus.NOT_FOUND, ErrorCode.REFUND_ORDER_NOT_FOUND, "Không tìm thấy đơn hàng.");
  }

  private static PaymentException adminRequestInvalid(String message) {
    return fail(HttpStatus.CONFLICT, ErrorCode.REFUND_ADMIN_REQUEST_INVALID, message);
  }

  private static PaymentException fail(HttpStatus status, ErrorCode code, String message) {
    return new PaymentException(status, code, message);
  }
}
