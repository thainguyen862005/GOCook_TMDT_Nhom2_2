package com.james.LMS.pricing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FixedMenuPreview(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int guestCount,
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal total,
    String currency,
    OffsetDateTime priceExpiresAt) {}
