package com.james.LMS.refund;

import static org.assertj.core.api.Assertions.assertThat;

import com.james.LMS.refund.RefundPolicy.Decision;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundPolicyTest {
  static final BigDecimal GROSS = new BigDecimal("1000000.00");
  static final LocalDateTime COOKING = LocalDateTime.of(2026, 10, 24, 18, 0);

  final RefundProperties properties = new RefundProperties();
  final RefundPolicy policy = new RefundPolicy(properties);

  CancellationContext customerCancelsHoursBefore(long hours, long extraMinutes) {
    OffsetDateTime at =
        COOKING.minusHours(hours).minusMinutes(extraMinutes).atOffset(java.time.ZoneOffset.ofHours(7));
    return new CancellationContext(8L, CancelledBy.CUSTOMER, at, COOKING, "doi lich");
  }

  Decision decide(CancellationContext context) {
    return policy.decide(context, GROSS);
  }

  @Test
  void aCustomerCancellingFortyNineHoursAheadGetsEverythingBack() {
    Decision decision = decide(customerCancelsHoursBefore(49, 0));

    assertThat(decision.percent()).isEqualTo(100);
    assertThat(decision.amount()).isEqualByComparingTo("1000000");
    assertThat(decision.retained()).isEqualByComparingTo("0");
  }

  @Test
  void thirtyHoursAheadGivesHalf() {
    Decision decision = decide(customerCancelsHoursBefore(30, 0));

    assertThat(decision.percent()).isEqualTo(50);
    assertThat(decision.amount()).isEqualByComparingTo("500000");
    assertThat(decision.retained()).isEqualByComparingTo("500000");
  }

  @Test
  void twoHoursAheadGivesNothingBackAndKeepsAllOfIt() {
    Decision decision = decide(customerCancelsHoursBefore(2, 0));

    assertThat(decision.percent()).isZero();
    assertThat(decision.amount()).isEqualByComparingTo("0");
    assertThat(decision.retained()).isEqualByComparingTo("1000000");
  }

  @Test
  void theTierEdgesBelongToTheBetterTier() {
    assertThat(decide(customerCancelsHoursBefore(48, 0)).percent()).isEqualTo(100);
    assertThat(decide(customerCancelsHoursBefore(48, 1)).percent()).isEqualTo(100);
    assertThat(decide(customerCancelsHoursBefore(47, 59)).percent()).isEqualTo(50);
    assertThat(decide(customerCancelsHoursBefore(24, 0)).percent()).isEqualTo(50);
    assertThat(decide(customerCancelsHoursBefore(23, 59)).percent()).isZero();
  }

  @Test
  void cancellingAfterTheCookingTimeGivesNothing() {
    CancellationContext late =
        new CancellationContext(
            8L, CancelledBy.CUSTOMER, COOKING.plusHours(1).atOffset(java.time.ZoneOffset.ofHours(7)), COOKING, null);

    assertThat(decide(late).percent()).isZero();
  }

  @Test
  void theCancellationInstantIsReadInVietnamTimeWhateverOffsetItCarries() {
    // 2026-10-22T11:00Z is 18:00 in Ho Chi Minh: exactly 48 hours before 2026-10-24T18:00.
    CancellationContext utc =
        new CancellationContext(
            8L, CancelledBy.CUSTOMER, OffsetDateTime.parse("2026-10-22T11:00:00Z"), COOKING, null);

    assertThat(decide(utc).percent()).isEqualTo(100);
  }

  @Test
  void aChefRejectionAndAnAdminCancellationRefundEverythingWhateverTheTiming() {
    for (CancelledBy by : new CancelledBy[] {CancelledBy.CHEF, CancelledBy.ADMIN}) {
      CancellationContext lastMinute =
          new CancellationContext(8L, by, COOKING.minusMinutes(5).atOffset(java.time.ZoneOffset.ofHours(7)), COOKING, null);

      assertThat(decide(lastMinute).percent()).isEqualTo(100);
      assertThat(decide(lastMinute).amount()).isEqualByComparingTo("1000000");
    }
  }

  @Test
  void amountsAreWholeVndRoundedDown() {
    Decision half = policy.decide(customerCancelsHoursBefore(30, 0), new BigDecimal("100001.00"));
    Decision third = policy.decide(customerCancelsHoursBefore(30, 0), new BigDecimal("333333.00"));

    assertThat(half.amount()).isEqualByComparingTo("50000");
    assertThat(half.retained()).isEqualByComparingTo("50001");
    assertThat(third.amount()).isEqualByComparingTo("166666");
    assertThat(third.amount().add(third.retained())).isEqualByComparingTo("333333");
  }

  @Test
  void theTiersAndPercentagesComeFromConfiguration() {
    properties.setCustomerTiers(List.of(new RefundProperties.Tier(72, 100), new RefundProperties.Tier(6, 20)));
    properties.setChefPercent(90);

    assertThat(decide(customerCancelsHoursBefore(73, 0)).percent()).isEqualTo(100);
    assertThat(decide(customerCancelsHoursBefore(49, 0)).percent()).isEqualTo(20);
    assertThat(decide(customerCancelsHoursBefore(2, 0)).percent()).isZero();
    assertThat(
            decide(new CancellationContext(8L, CancelledBy.CHEF, OffsetDateTime.now(), COOKING, null)).percent())
        .isEqualTo(90);
  }
}
