package com.james.LMS.refund;

import java.math.BigDecimal;

/** Sends the money back to the customer. Today the only implementation is simulated. */
public interface RefundGateway {

  RefundOutcome refund(RefundRequest request);

  record RefundRequest(Long refundId, Long paymentId, BigDecimal amount, RefundReason reason) {}

  record RefundOutcome(boolean success, String reference, String message) {
    public static RefundOutcome success(String reference) {
      return new RefundOutcome(true, reference, null);
    }

    public static RefundOutcome failure(String message) {
      return new RefundOutcome(false, null, message);
    }
  }
}
