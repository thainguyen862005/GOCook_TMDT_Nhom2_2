package com.james.LMS.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PaymentOrderCheckoutRequest(
    @NotBlank @Size(max = 50) String orderCode, @NotNull UUID idempotencyKey) {}
