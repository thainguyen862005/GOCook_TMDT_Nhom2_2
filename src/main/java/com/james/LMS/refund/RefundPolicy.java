package com.james.LMS.refund;

import com.james.LMS.refund.RefundProperties.Tier;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** How much of the held money goes back, from the configured policy. Pure: no database. */
@Component
@RequiredArgsConstructor
public class RefundPolicy {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final RefundProperties properties;

  /** @param retained what stays: gross - amount */
  public record Decision(int percent, BigDecimal amount, BigDecimal retained) {}

  public Decision decide(CancellationContext context, BigDecimal gross) {
    int percent =
        switch (context.cancelledBy()) {
          case CHEF -> properties.getChefPercent();
          case ADMIN -> properties.getAdminPercent();
          case CUSTOMER -> customerPercent(context);
        };
    BigDecimal amount = gross.multiply(BigDecimal.valueOf(percent)).divide(HUNDRED, 0, RoundingMode.DOWN);
    return new Decision(percent, amount, gross.subtract(amount));
  }

  private int customerPercent(CancellationContext context) {
    long minutesLeft =
        Duration.between(
                context
                    .cancelledAt()
                    .atZoneSameInstant(PaymentGateway.GATEWAY_ZONE)
                    .toLocalDateTime(),
                context.scheduledAt())
            .toMinutes();
    return properties.getCustomerTiers().stream()
        .sorted(Comparator.comparingInt(Tier::getMinHours).reversed())
        .filter(tier -> minutesLeft >= tier.getMinHours() * 60L)
        .map(Tier::getPercent)
        .findFirst()
        .orElse(0);
  }
}
