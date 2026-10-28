package com.james.LMS.payment;

import com.james.LMS.service.PaymentGateway;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** Business time for the payment read APIs: Asia/Ho_Chi_Minh, ISO text with the +07:00 offset. */
public final class PaymentTime {
  private PaymentTime() {}

  public static String iso(OffsetDateTime time) {
    return time == null
        ? null
        : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time.atZoneSameInstant(PaymentGateway.GATEWAY_ZONE));
  }

  public static String isoMillis(Long epochMillis) {
    return epochMillis == null
        ? null
        : iso(Instant.ofEpochMilli(epochMillis).atZone(PaymentGateway.GATEWAY_ZONE).toOffsetDateTime());
  }

  /** First instant of a local day. */
  public static OffsetDateTime startOfDay(LocalDate day) {
    return day.atStartOfDay(PaymentGateway.GATEWAY_ZONE).toOffsetDateTime();
  }
}
