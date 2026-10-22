package com.james.LMS.refund;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * What the order module tells the refund module after it cancelled or rejected an order.
 *
 * @param cancelledAt when the cancellation happened
 * @param scheduledAt the cooking time of the order (orders.scheduled_at, Asia/Ho_Chi_Minh)
 * @param reason free text kept in the ledger note
 */
public record CancellationContext(
    Long orderId,
    CancelledBy cancelledBy,
    OffsetDateTime cancelledAt,
    LocalDateTime scheduledAt,
    String reason) {}
