package com.james.LMS.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PaymentCheckoutRequest(
    @NotNull Long productId,
    @NotNull @Min(1) Integer guestCount,
    @NotNull LocalDateTime scheduledAt,
    @NotBlank @Size(max = 255) String customerName,
    @NotBlank @Pattern(regexp = "^[0-9+\\-\\s]{8,30}$") String customerPhone,
    @NotBlank @Size(max = 1000) String serviceAddress,
    @Size(max = 2000) String customerNote) {}
