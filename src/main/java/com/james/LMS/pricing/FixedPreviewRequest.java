package com.james.LMS.pricing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FixedPreviewRequest(
    @NotNull Long productId, @NotNull @Min(1) Integer guestCount, String voucherCode) {}
