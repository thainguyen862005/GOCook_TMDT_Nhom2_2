package com.james.LMS.refund;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One row of the refunds table. */
public record Refund(
    Long id,
    Long orderId,
    Long paymentTransactionId,
    BigDecimal amount,
    BigDecimal retainedAmount,
    int policyPercent,
    RefundReason reasonCode,
    RefundStatus status,
    String gatewayReference,
    String failureMessage,
    Long adminRefundRequestId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
