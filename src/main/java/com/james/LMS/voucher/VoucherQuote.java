package com.james.LMS.voucher;

import java.math.BigDecimal;

/** The discount a voucher gives on one subtotal, whole VND. */
public record VoucherQuote(Long voucherId, String code, BigDecimal discountAmount) {}
