package com.james.LMS.payment.finance;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.PaymentTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;

/**
 * A range of local days (Asia/Ho_Chi_Minh), both ends included, as instants: [fromInclusive,
 * toExclusive). Either end may be missing.
 */
public record FinanceRange(OffsetDateTime fromInclusive, OffsetDateTime toExclusive) {

  public static FinanceRange of(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new PaymentException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.FINANCE_INVALID_RANGE,
          "Ngày bắt đầu không được sau ngày kết thúc.");
    }
    return new FinanceRange(
        from == null ? null : PaymentTime.startOfDay(from),
        to == null ? null : PaymentTime.startOfDay(to.plusDays(1)));
  }

  public boolean contains(OffsetDateTime instant) {
    return (fromInclusive == null || !instant.isBefore(fromInclusive))
        && (toExclusive == null || instant.isBefore(toExclusive));
  }
}
