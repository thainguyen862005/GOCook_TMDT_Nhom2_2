package com.james.LMS.pricing;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

  private Duration previewTtl = Duration.ofMinutes(15);

  private Guidance guidance = new Guidance();

  @Getter
  @Setter
  public static class Guidance {
    /** Menus of the calling chef (same cuisine) needed before the chef's own median is used. */
    private int minChefSamples = 3;

    /** Published menus of the whole market (same cuisine) needed when the chef has too few. */
    private int minMarketSamples = 5;

    /** Half-width of the suggested range around the adjusted price (0.20 = ±20%). */
    private BigDecimal rangeSpread = new BigDecimal("0.20");

    private int referenceDishCount = 4;
    private BigDecimal dishStep = new BigDecimal("0.04");
    private BigDecimal dishCap = new BigDecimal("0.20");

    private BigDecimal dietaryStep = new BigDecimal("0.05");
    private BigDecimal allergyStep = new BigDecimal("0.04");
    private BigDecimal specialNeedsCap = new BigDecimal("0.20");

    private int referenceDurationMinutes = 180;
    private BigDecimal durationStepPerHour = new BigDecimal("0.06");
    private BigDecimal durationCap = new BigDecimal("0.20");

    /** Lower-case service area -> impact (0.05 = +5%). Empty by default: no area factor. */
    private Map<String, BigDecimal> areaImpact = new HashMap<>();
  }
}
