package com.james.LMS.refund;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** The refund policy lives here, not in code or UI. Percentages are of the amount held in escrow. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gocook.refund")
public class RefundProperties {

  /** Refund when the chef rejects the order. */
  private int chefPercent = 100;

  /** Refund when an admin cancels the order. */
  private int adminPercent = 100;

  /**
   * Customer cancellation: the first tier (highest minHours first) whose minHours is not more than
   * the hours left until the cooking time gives the percent. Below every tier nothing is refunded.
   */
  private List<Tier> customerTiers =
      new ArrayList<>(List.of(new Tier(48, 100), new Tier(24, 50), new Tier(0, 0)));

  /** A refund waiting longer than this shows up in the admin reconciliation report. */
  private Duration pendingMaxAge = Duration.ofHours(24);

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tier {
    private int minHours;
    private int percent;
  }
}
