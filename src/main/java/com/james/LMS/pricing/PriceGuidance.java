package com.james.LMS.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Price hint for a chef answering a custom request. Reference only: the chef decides the final
 * price. Built purely from published menu prices, never from ingredient or supermarket prices.
 */
public record PriceGuidance(
    Long requestId,
    Status status,
    String reason,
    String currency,
    int guestCount,
    Basis basis,
    int sampleSize,
    BigDecimal baselinePerGuest,
    BigDecimal suggestedMin,
    BigDecimal suggestedMax,
    List<Factor> factors,
    BudgetPosition budgetPosition,
    String warning) {

  public enum Status {
    OK,
    INSUFFICIENT_DATA
  }

  public enum Basis {
    CHEF,
    MARKET
  }

  public enum BudgetPosition {
    WITHIN,
    BELOW_RANGE,
    ABOVE_RANGE,
    NA
  }

  /** How a proposed price compares with the suggested range. */
  public enum PriceVerdict {
    IN_RANGE,
    BELOW,
    ABOVE,
    NO_GUIDANCE
  }

  public record Factor(String code, String label, BigDecimal impactPercent, String explanation) {}

  static PriceGuidance insufficient(Long requestId, int guestCount, String reason, int sampleSize) {
    return new PriceGuidance(
        requestId,
        Status.INSUFFICIENT_DATA,
        reason,
        "VND",
        guestCount,
        null,
        sampleSize,
        null,
        null,
        null,
        List.of(),
        BudgetPosition.NA,
        null);
  }

  public PriceVerdict verdictFor(BigDecimal price) {
    if (status != Status.OK) {
      return PriceVerdict.NO_GUIDANCE;
    }
    if (price.compareTo(suggestedMin) < 0) {
      return PriceVerdict.BELOW;
    }
    return price.compareTo(suggestedMax) > 0 ? PriceVerdict.ABOVE : PriceVerdict.IN_RANGE;
  }
}
