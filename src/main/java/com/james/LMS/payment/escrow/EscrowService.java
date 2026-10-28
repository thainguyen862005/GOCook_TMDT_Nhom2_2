package com.james.LMS.payment.escrow;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pays the held money out to the chef when the customer confirms the order. The database function
 * gocook_accept_and_release stays the authority (it moves the order to COMPLETED, the escrow to
 * RELEASED and writes the ledger); this class only adds the dispute guard and readable errors.
 * The order's confirm command must call {@link #release} instead of changing the status itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowService {

  public enum ReleaseResult {
    RELEASED,
    ALREADY_RELEASED
  }

  private final EscrowRepository escrows;
  private final TransactionTemplate transactions;

  public ReleaseResult release(Long orderId, Long customerId) {
    return transactions.execute(
        status -> {
          Long owner =
              escrows
                  .lockOrderCustomer(orderId)
                  .orElseThrow(
                      () ->
                          fail(
                              HttpStatus.NOT_FOUND,
                              ErrorCode.ESCROW_ORDER_NOT_FOUND,
                              "Không tìm thấy đơn hàng."));
          if (!owner.equals(customerId)) {
            throw notOwner();
          }
          if (!escrows.isReleased(orderId) && escrows.hasOpenDispute(orderId)) {
            throw fail(
                HttpStatus.CONFLICT, ErrorCode.ESCROW_DISPUTE_OPEN, "Đơn đang có khiếu nại.");
          }

          Boolean released;
          try {
            released = escrows.acceptAndRelease(orderId, customerId);
          } catch (RuntimeException e) {
            throw explain(e);
          }
          if (Boolean.TRUE.equals(released)) {
            log.info("escrow released order={}", orderId);
            return ReleaseResult.RELEASED;
          }
          return ReleaseResult.ALREADY_RELEASED;
        });
  }

  /** The one place where messages of gocook_accept_and_release become coded errors. */
  private static RuntimeException explain(RuntimeException error) {
    String detail = String.valueOf(NestedExceptionUtils.getMostSpecificCause(error).getMessage());
    if (detail.contains("is not order owner")) {
      return notOwner();
    }
    if (detail.contains("must await acceptance")) {
      return fail(
          HttpStatus.CONFLICT,
          ErrorCode.ESCROW_ORDER_NOT_AWAITING,
          "Đơn chưa ở trạng thái chờ nghiệm thu.");
    }
    if (detail.contains("No HELD escrow")) {
      return fail(
          HttpStatus.CONFLICT,
          ErrorCode.ESCROW_NOT_HELD,
          "Đơn không có khoản tiền đang được giữ.");
    }
    if (detail.contains("not found")) {
      return fail(HttpStatus.NOT_FOUND, ErrorCode.ESCROW_ORDER_NOT_FOUND, "Không tìm thấy đơn hàng.");
    }
    return error;
  }

  private static PaymentException notOwner() {
    return fail(
        HttpStatus.FORBIDDEN,
        ErrorCode.ESCROW_NOT_OWNER,
        "Chỉ khách hàng của đơn mới được nghiệm thu.");
  }

  private static PaymentException fail(HttpStatus status, ErrorCode code, String message) {
    return new PaymentException(status, code, message);
  }
}
