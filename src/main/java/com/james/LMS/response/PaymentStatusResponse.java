package com.james.LMS.response;

import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record PaymentStatusResponse(
    String orderCode,
    PaymentStatus paymentStatus,
    OrderStatus orderStatus,
    String productName,
    String chefName,
    Integer guestCount,
    LocalDateTime scheduledAt,
    BigDecimal amount,
    OffsetDateTime paidAt) {}
