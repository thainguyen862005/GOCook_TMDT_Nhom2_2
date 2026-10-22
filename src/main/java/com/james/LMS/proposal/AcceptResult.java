package com.james.LMS.proposal;

import java.math.BigDecimal;

/** orderCode is the booking's code: the customer pays it next (see checkout, C-10). */
public record AcceptResult(
    Long proposalId, Long bookingId, String orderCode, BigDecimal totalAmount) {}
