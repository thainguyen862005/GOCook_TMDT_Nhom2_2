package com.james.LMS.pricing;

import static com.james.LMS.proposal.TestPorts.CHEF;
import static com.james.LMS.proposal.TestPorts.REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.pricing.PriceGuidance.Basis;
import com.james.LMS.pricing.PriceGuidance.BudgetPosition;
import com.james.LMS.pricing.PriceGuidance.PriceVerdict;
import com.james.LMS.pricing.PriceGuidance.Status;
import com.james.LMS.proposal.ProposalException;
import com.james.LMS.proposal.TestPorts;
import com.james.LMS.proposal.TestPorts.FakeRequestPort;
import com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class QuoteGuidanceServiceTest {
  final MenuPriceReader menus = mock(MenuPriceReader.class);
  final PricingProperties properties = new PricingProperties();
  final FakeRequestPort requests = new FakeRequestPort(TestPorts.snapshot());
  final QuoteGuidanceService service =
      new QuoteGuidanceService(menus, properties, TestPorts.ports(requests, null));

  static List<BigDecimal> prices(long... values) {
    return IntStream.range(0, values.length).mapToObj(i -> BigDecimal.valueOf(values[i])).toList();
  }

  void market(long... values) {
    when(menus.chefPrices(any(), any())).thenReturn(List.of());
    when(menus.marketPrices(any())).thenReturn(prices(values));
  }

  RequestSnapshot request(int guests, BigDecimal budgetMin, BigDecimal budgetMax) {
    RequestSnapshot base = TestPorts.snapshot();
    return new RequestSnapshot(
        base.requestId(),
        base.customerId(),
        true,
        guests,
        budgetMin,
        budgetMax,
        base.matchedChefIds(),
        "Mon Viet",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  RequestSnapshot withFactors(
      Integer dishes, Set<String> dietary, Set<String> allergies, Integer minutes, String area) {
    RequestSnapshot base = request(4, null, null);
    return new RequestSnapshot(
        base.requestId(),
        base.customerId(),
        true,
        4,
        null,
        null,
        base.matchedChefIds(),
        "Mon Viet",
        dishes,
        dietary,
        allergies,
        area,
        minutes,
        null);
  }

  @Test
  void usesTheChefsOwnMedianWhenTheChefHasEnoughMenus() {
    when(menus.chefPrices(CHEF, "Mon Viet")).thenReturn(prices(90000, 100000, 140000));

    PriceGuidance guidance = service.guide(CHEF, request(4, null, null));

    assertThat(guidance.status()).isEqualTo(Status.OK);
    assertThat(guidance.basis()).isEqualTo(Basis.CHEF);
    assertThat(guidance.sampleSize()).isEqualTo(3);
    assertThat(guidance.baselinePerGuest()).isEqualByComparingTo("100000");
    assertThat(guidance.suggestedMin()).isEqualByComparingTo("320000");
    assertThat(guidance.suggestedMax()).isEqualByComparingTo("480000");
    assertThat(guidance.factors()).isEmpty();
  }

  @Test
  void fallsBackToTheMarketMedianWhenTheChefHasTooFewMenus() {
    when(menus.chefPrices(CHEF, "Mon Viet")).thenReturn(prices(500000, 600000));
    when(menus.marketPrices("Mon Viet")).thenReturn(prices(80000, 90000, 100000, 110000, 120000));

    PriceGuidance guidance = service.guide(CHEF, request(4, null, null));

    assertThat(guidance.basis()).isEqualTo(Basis.MARKET);
    assertThat(guidance.sampleSize()).isEqualTo(5);
    assertThat(guidance.baselinePerGuest()).isEqualByComparingTo("100000");
  }

  @Test
  void evenSampleUsesTheAverageOfTheTwoMiddlePrices() {
    market(100000, 110000, 120000, 130000, 140000, 150000);

    assertThat(service.guide(CHEF, request(1, null, null)).baselinePerGuest())
        .isEqualByComparingTo("125000");
  }

  @Test
  void tooFewMenusGivesNoRangeInsteadOfAGuess() {
    market(100000, 110000, 120000, 130000);

    PriceGuidance guidance = service.guide(CHEF, request(4, null, null));

    assertThat(guidance.status()).isEqualTo(Status.INSUFFICIENT_DATA);
    assertThat(guidance.reason()).isEqualTo("NOT_ENOUGH_MENUS");
    assertThat(guidance.sampleSize()).isEqualTo(4);
    assertThat(guidance.suggestedMin()).isNull();
    assertThat(guidance.suggestedMax()).isNull();
    assertThat(guidance.budgetPosition()).isEqualTo(BudgetPosition.NA);
    assertThat(guidance.verdictFor(new BigDecimal("1"))).isEqualTo(PriceVerdict.NO_GUIDANCE);
  }

  @Test
  void requestWithoutCuisineHasNoGuidance() {
    RequestSnapshot base = request(4, null, null);
    RequestSnapshot noCuisine =
        new RequestSnapshot(
            base.requestId(), base.customerId(), true, 4, null, null, base.matchedChefIds(), " ",
            null, null, null, null, null, null);

    PriceGuidance guidance = service.guide(CHEF, noCuisine);

    assertThat(guidance.status()).isEqualTo(Status.INSUFFICIENT_DATA);
    assertThat(guidance.reason()).isEqualTo("NO_CUISINE");
  }

  @Test
  void rangeScalesWithTheNumberOfGuestsFromOneToThirty() {
    market(100000, 100000, 100000, 100000, 100000);

    PriceGuidance one = service.guide(CHEF, request(1, null, null));
    PriceGuidance thirty = service.guide(CHEF, request(30, null, null));

    assertThat(one.suggestedMin()).isEqualByComparingTo("80000");
    assertThat(one.suggestedMax()).isEqualByComparingTo("120000");
    assertThat(thirty.suggestedMin()).isEqualByComparingTo("2400000");
    assertThat(thirty.suggestedMax()).isEqualByComparingTo("3600000");
  }

  @Test
  void invalidGuestCountHasNoGuidance() {
    market(100000, 100000, 100000, 100000, 100000);

    assertThat(service.guide(CHEF, request(0, null, null)).reason()).isEqualTo("INVALID_GUEST_COUNT");
  }

  @Test
  void budgetBelowTheRangeIsWarned() {
    market(100000, 100000, 100000, 100000, 100000);

    PriceGuidance guidance = service.guide(CHEF, request(4, new BigDecimal("100000"), new BigDecimal("200000")));

    assertThat(guidance.budgetPosition()).isEqualTo(BudgetPosition.BELOW_RANGE);
    assertThat(guidance.warning()).isNotBlank();
  }

  @Test
  void budgetAboveTheRangeIsWarned() {
    market(100000, 100000, 100000, 100000, 100000);

    PriceGuidance guidance = service.guide(CHEF, request(4, new BigDecimal("900000"), new BigDecimal("1200000")));

    assertThat(guidance.budgetPosition()).isEqualTo(BudgetPosition.ABOVE_RANGE);
    assertThat(guidance.warning()).isNotBlank();
  }

  @Test
  void budgetOverlappingTheRangeIsWithinAndMissingBudgetIsNotApplicable() {
    market(100000, 100000, 100000, 100000, 100000);

    assertThat(service.guide(CHEF, request(4, new BigDecimal("400000"), new BigDecimal("600000"))).budgetPosition())
        .isEqualTo(BudgetPosition.WITHIN);
    assertThat(service.guide(CHEF, request(4, null, new BigDecimal("350000"))).budgetPosition())
        .isEqualTo(BudgetPosition.WITHIN);
    assertThat(service.guide(CHEF, request(4, null, null)).budgetPosition())
        .isEqualTo(BudgetPosition.NA);
  }

  @Test
  void dietaryAndAllergyNeedsRaiseThePriceAndAreExplained() {
    market(100000, 100000, 100000, 100000, 100000);

    PriceGuidance guidance =
        service.guide(CHEF, withFactors(null, Set.of("vegan", "halal"), Set.of("peanut"), null, null));

    assertThat(guidance.factors()).extracting(PriceGuidance.Factor::code).containsExactly("DIETARY", "ALLERGY");
    assertThat(guidance.factors().get(0).impactPercent()).isEqualByComparingTo("10.0");
    assertThat(guidance.factors().get(1).impactPercent()).isEqualByComparingTo("4.0");
    assertThat(guidance.factors()).allSatisfy(factor -> assertThat(factor.explanation()).isNotBlank());
    assertThat(guidance.suggestedMin()).isEqualByComparingTo("364800");
    assertThat(guidance.suggestedMax()).isEqualByComparingTo("547200");
  }

  @Test
  void specialNeedsImpactIsCapped() {
    market(100000, 100000, 100000, 100000, 100000);
    Set<String> many = IntStream.range(0, 12).mapToObj(i -> "tag" + i).collect(Collectors.toSet());

    PriceGuidance guidance = service.guide(CHEF, withFactors(null, many, Collections.emptySet(), null, null));

    assertThat(guidance.factors()).hasSize(1);
    assertThat(guidance.factors().get(0).impactPercent()).isEqualByComparingTo("20.0");
  }

  @Test
  void dishCountDurationAndAreaMoveThePrice() {
    market(100000, 100000, 100000, 100000, 100000);
    properties.getGuidance().getAreaImpact().put("quan 1", new BigDecimal("0.05"));

    PriceGuidance guidance = service.guide(CHEF, withFactors(6, null, null, 300, " Quan 1 "));

    assertThat(guidance.factors())
        .extracting(PriceGuidance.Factor::code)
        .containsExactly("DISH_COUNT", "DURATION", "AREA");
    assertThat(guidance.factors().get(0).impactPercent()).isEqualByComparingTo("8.0");
    assertThat(guidance.factors().get(1).impactPercent()).isEqualByComparingTo("12.0");
    assertThat(guidance.factors().get(2).impactPercent()).isEqualByComparingTo("5.0");
    assertThat(guidance.suggestedMin()).isEqualByComparingTo("400000");
  }

  @Test
  void fewerDishesAndShorterEventLowerThePrice() {
    market(100000, 100000, 100000, 100000, 100000);

    PriceGuidance guidance = service.guide(CHEF, withFactors(1, null, null, 30, null));

    assertThat(guidance.factors().get(0).impactPercent()).isEqualByComparingTo("-12.0");
    assertThat(guidance.factors().get(1).impactPercent()).isEqualByComparingTo("-15.0");
  }

  @Test
  void verdictComparesAPriceWithTheRange() {
    market(100000, 100000, 100000, 100000, 100000);
    PriceGuidance guidance = service.guide(CHEF, request(4, null, null));

    assertThat(guidance.verdictFor(new BigDecimal("319999"))).isEqualTo(PriceVerdict.BELOW);
    assertThat(guidance.verdictFor(new BigDecimal("320000"))).isEqualTo(PriceVerdict.IN_RANGE);
    assertThat(guidance.verdictFor(new BigDecimal("480000"))).isEqualTo(PriceVerdict.IN_RANGE);
    assertThat(guidance.verdictFor(new BigDecimal("480001"))).isEqualTo(PriceVerdict.ABOVE);
  }

  @Test
  void onlyMatchedChefsMayAskForGuidance() {
    market(100000, 100000, 100000, 100000, 100000);

    assertThat(service.guidanceFor(CHEF, REQUEST_ID).status()).isEqualTo(Status.OK);
    assertThatThrownBy(() -> service.guidanceFor(99L, REQUEST_ID))
        .isInstanceOfSatisfying(
            ProposalException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_CHEF_NOT_MATCHED));
  }

  @Test
  void unknownRequestIsNotFoundAndMissingPortIsNotImplemented() {
    assertThatThrownBy(() -> service.guidanceFor(CHEF, 404L))
        .isInstanceOfSatisfying(
            ProposalException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_REQUEST_NOT_FOUND));

    QuoteGuidanceService noPort =
        new QuoteGuidanceService(menus, properties, TestPorts.ports(null, null));
    assertThatThrownBy(() -> noPort.guidanceFor(CHEF, REQUEST_ID))
        .isInstanceOfSatisfying(
            ProposalException.class,
            error -> {
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_PORT_NOT_INTEGRATED);
              assertThat(error.getStatus().value()).isEqualTo(501);
            });
  }
}
