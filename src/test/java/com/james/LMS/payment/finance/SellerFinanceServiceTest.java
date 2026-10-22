package com.james.LMS.payment.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.finance.SellerFinanceService.TransactionPage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class SellerFinanceServiceTest {
  static final long CHEF = 40L;

  final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  final SellerFinanceService service = new SellerFinanceService(jdbc);

  static OffsetDateTime at(String text) {
    return OffsetDateTime.parse(text);
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void dateRangesAreInclusiveLocalDaysInVietnamTime() {
    FinanceRange october = FinanceRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    assertThat(october.fromInclusive()).isEqualTo(at("2026-10-01T00:00:00+07:00"));
    assertThat(october.toExclusive()).isEqualTo(at("2026-11-01T00:00:00+07:00"));
    assertThat(october.contains(at("2026-10-01T00:00:00+07:00"))).isTrue();
    assertThat(october.contains(at("2026-10-31T23:59:59+07:00"))).isTrue();
  }

  @Test
  void halfPastElevenOnTheLastOfOctoberIsOctoberAndMidnightIsNovember() {
    FinanceRange october = FinanceRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
    FinanceRange november = FinanceRange.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    OffsetDateTime beforeMidnight = at("2026-10-31T23:30:00+07:00");
    OffsetDateTime afterMidnight = at("2026-11-01T00:00:00+07:00");

    assertThat(october.contains(beforeMidnight)).isTrue();
    assertThat(november.contains(beforeMidnight)).isFalse();
    assertThat(october.contains(afterMidnight)).isFalse();
    assertThat(november.contains(afterMidnight)).isTrue();
    // The same instant written in UTC lands on the same side.
    assertThat(october.contains(at("2026-10-31T16:30:00Z"))).isTrue();
    assertThat(november.contains(at("2026-10-31T17:00:00Z"))).isTrue();
  }

  @Test
  void aRangeMayLackOneOrBothEnds() {
    FinanceRange open = FinanceRange.of(null, null);

    assertThat(open.contains(at("2000-01-01T00:00:00+07:00"))).isTrue();
    assertThat(FinanceRange.of(LocalDate.of(2026, 10, 5), null).contains(at("2026-10-04T23:59:59+07:00"))).isFalse();
    assertThat(FinanceRange.of(null, LocalDate.of(2026, 10, 5)).contains(at("2026-10-06T00:00:00+07:00"))).isFalse();
  }

  @Test
  void aReversedRangeIsACodedBadRequest() {
    assertThatThrownBy(() -> FinanceRange.of(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)))
        .isInstanceOfSatisfying(
            PaymentException.class,
            error -> {
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FINANCE_INVALID_RANGE);
              assertThat(error.getStatus().value()).isEqualTo(400);
            });
  }

  @Test
  void theMonthWindowEndsWithTheCurrentMonthOldestFirstAcrossAYearBoundary() {
    List<YearMonth> window = SellerFinanceService.lastMonths(YearMonth.of(2026, 2), 4);

    assertThat(window)
        .containsExactly(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    assertThat(SellerFinanceService.startOf(YearMonth.of(2026, 11))).isEqualTo(at("2026-11-01T00:00:00+07:00"));
  }

  @Test
  void theFeePercentageIsReadFromTheRowNotFixed() {
    assertThat(SellerFinanceService.percent(new BigDecimal("150000"), new BigDecimal("1000000")))
        .isEqualByComparingTo("15.00");
    assertThat(SellerFinanceService.percent(new BigDecimal("100000"), new BigDecimal("1000000")))
        .isEqualByComparingTo("10.00");
    assertThat(SellerFinanceService.percent(new BigDecimal("49999.95"), new BigDecimal("333333")))
        .isEqualByComparingTo("15.00");
    assertThat(SellerFinanceService.percent(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualByComparingTo("0");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void thePageSizeIsCappedAtOneHundredAndTheChefComesFromTheCaller() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

    TransactionPage page = service.transactions(CHEF, 2, 1000, null, FinanceRange.of(null, null));

    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
    assertThat(page.size()).isEqualTo(100);
    assertThat(params.getValue().getValue("limit")).isEqualTo(100);
    assertThat(params.getValue().getValue("offset")).isEqualTo(200L);
    assertThat(params.getValue().getValue("chef")).isEqualTo(CHEF);
  }

  @Test
  void badPagingOrStatusOrMonthsAreCodedBadRequests() {
    FinanceRange any = FinanceRange.of(null, null);

    assertRejected(() -> service.transactions(CHEF, -1, 20, null, any), ErrorCode.FINANCE_INVALID_PARAM);
    assertRejected(() -> service.transactions(CHEF, 0, 0, null, any), ErrorCode.FINANCE_INVALID_PARAM);
    assertRejected(() -> service.transactions(CHEF, 0, 20, "REFUNDED", any), ErrorCode.FINANCE_INVALID_PARAM);
    assertRejected(() -> service.monthly(CHEF, 0), ErrorCode.FINANCE_INVALID_PARAM);
    assertRejected(() -> service.monthly(CHEF, 25), ErrorCode.FINANCE_INVALID_PARAM);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void theSummaryOnlyCountsHeldAndReleasedEscrowsAndAppliesOnlyTheGivenBounds() {
    service.summary(CHEF, FinanceRange.of(LocalDate.of(2026, 10, 1), null));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).queryForObject(sql.capture(), params.capture(), any(RowMapper.class));
    assertThat(sql.getValue())
        .contains("e.status IN ('HELD', 'RELEASED')")
        .contains("e.released_at >= :from")
        .contains("e.held_at >= :from")
        .doesNotContain(":to");
    assertThat(params.getValue().hasValue("from")).isTrue();
    assertThat(params.getValue().hasValue("to")).isFalse();
  }
}
