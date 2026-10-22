package com.james.LMS.voucher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.voucher.VoucherService.Voucher;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class VoucherServiceTest {
  static final long CUSTOMER = 5L;
  static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T10:00:00+07:00");

  final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  final VoucherProperties properties = new VoucherProperties();
  final VoucherService service = spy(new VoucherService(jdbc, properties));

  Voucher voucher(String type, String value, String min, String max, int limit, int used) {
    return new Voucher(
        9L,
        "SALE10",
        type,
        new BigDecimal(value),
        new BigDecimal(min),
        max == null ? null : new BigDecimal(max),
        limit,
        used,
        NOW.minusDays(1),
        NOW.plusDays(1),
        true);
  }

  Voucher percent(String value) {
    return voucher("PERCENT", value, "0", null, 100, 0);
  }

  void serve(Voucher voucher, int customerRedemptions) {
    doReturn(voucher).when(service).find(anyString(), anyBoolean());
    doReturn(customerRedemptions).when(service).customerRedemptions(any(), any());
  }

  BigDecimal discount(String subtotal) {
    return service
        .validateAndCalculate("SALE10", CUSTOMER, new BigDecimal(subtotal), NOW)
        .discountAmount();
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void anUnknownCodeIsRejected() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());

    assertRejected(() -> service.validateAndCalculate("NOPE", CUSTOMER, new BigDecimal("500000"), NOW), ErrorCode.VOUCHER_NOT_FOUND);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void theCodeIsTrimmedAndMatchedInUpperCaseAndLockedOnlyWhenReserving() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn((List) List.of(percent("10")));

    service.validateAndCalculate("  sale10 ", CUSTOMER, new BigDecimal("500000"), NOW);

    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
    assertThat(params.getValue().getValue("code")).isEqualTo("SALE10");
    assertThat(sql.getValue()).doesNotContain("FOR UPDATE");
  }

  @Test
  void aVoucherThatIsNotActiveIsRejected() {
    Voucher off = voucher("PERCENT", "10", "0", null, 100, 0);
    serve(new Voucher(off.id(), off.code(), off.discountType(), off.discountValue(), off.minOrderAmount(), off.maxDiscount(), off.usageLimit(), off.usedCount(), off.startsAt(), off.endsAt(), false), 0);

    assertRejected(() -> discount("500000"), ErrorCode.VOUCHER_INACTIVE);
  }

  @Test
  void aVoucherNotStartedYetIsRejectedAndTheStartInstantIsIncluded() {
    Voucher later =
        new Voucher(9L, "SALE10", "PERCENT", BigDecimal.TEN, BigDecimal.ZERO, null, 100, 0, NOW.plusSeconds(1), NOW.plusDays(1), true);
    serve(later, 0);
    assertRejected(() -> discount("500000"), ErrorCode.VOUCHER_NOT_STARTED);

    serve(new Voucher(9L, "SALE10", "PERCENT", BigDecimal.TEN, BigDecimal.ZERO, null, 100, 0, NOW, NOW.plusDays(1), true), 0);
    assertThat(discount("500000")).isEqualByComparingTo("50000");
  }

  @Test
  void aVoucherIsExpiredAtItsEndInstant() {
    serve(new Voucher(9L, "SALE10", "PERCENT", BigDecimal.TEN, BigDecimal.ZERO, null, 100, 0, NOW.minusDays(2), NOW, true), 0);
    assertRejected(() -> discount("500000"), ErrorCode.VOUCHER_EXPIRED);

    serve(new Voucher(9L, "SALE10", "PERCENT", BigDecimal.TEN, BigDecimal.ZERO, null, 100, 0, NOW.minusDays(2), NOW.plusSeconds(1), true), 0);
    assertThat(discount("500000")).isEqualByComparingTo("50000");
  }

  @Test
  void anOrderBelowTheMinimumIsRejectedAndTheMinimumItselfIsAccepted() {
    serve(voucher("PERCENT", "10", "100000", null, 100, 0), 0);

    assertRejected(() -> discount("99999"), ErrorCode.VOUCHER_MIN_ORDER);
    assertThat(discount("100000")).isEqualByComparingTo("10000");
  }

  @Test
  void theUsageLimitIsEnforced() {
    serve(voucher("PERCENT", "10", "0", null, 5, 5), 0);
    assertRejected(() -> discount("500000"), ErrorCode.VOUCHER_EXHAUSTED);

    serve(voucher("PERCENT", "10", "0", null, 5, 4), 0);
    assertThat(discount("500000")).isEqualByComparingTo("50000");
  }

  @Test
  void theCustomerLimitCountsReservedAndConsumedUsesAndIsConfigurable() {
    serve(percent("10"), 1);
    assertRejected(() -> discount("500000"), ErrorCode.VOUCHER_CUSTOMER_LIMIT);

    properties.setPerCustomerLimit(2);
    assertThat(discount("500000")).isEqualByComparingTo("50000");
    serve(percent("10"), 2);
    assertRejected(() -> discount("500000"), ErrorCode.VOUCHER_CUSTOMER_LIMIT);
  }

  @Test
  void percentIsRoundedDownToWholeVnd() {
    serve(percent("10"), 0);
    assertThat(discount("333333")).isEqualByComparingTo("33333");
    serve(percent("12.5"), 0);
    assertThat(discount("100001")).isEqualByComparingTo("12500");
    serve(percent("33.33"), 0);
    assertThat(discount("999")).isEqualByComparingTo("332");
  }

  @Test
  void percentIsCappedByMaxDiscount() {
    serve(voucher("PERCENT", "20", "0", "50000", 100, 0), 0);

    assertThat(discount("1000000")).isEqualByComparingTo("50000");
    assertThat(discount("100000")).isEqualByComparingTo("20000");
  }

  @Test
  void fixedGivesItsValueAndNeverMoreThanTheSubtotal() {
    serve(voucher("FIXED", "50000", "0", null, 100, 0), 0);
    assertThat(discount("360000")).isEqualByComparingTo("50000");
    assertThat(discount("30000")).isEqualByComparingTo("30000");

    serve(voucher("FIXED", "50000.75", "0", null, 100, 0), 0);
    assertThat(discount("360000")).isEqualByComparingTo("50000");
  }

  @Test
  void aHundredPercentVoucherIsCappedAtTheSubtotal() {
    serve(percent("100"), 0);

    assertThat(discount("250000")).isEqualByComparingTo("250000");
  }

  @Test
  void reserveLocksChecksCountsTheUseAndRecordsTheRedemption() {
    doReturn(percent("10")).when(service).find("SALE10", true);
    doReturn(0).when(service).customerRedemptions(any(), any());
    VoucherQuote priced = new VoucherQuote(9L, "SALE10", new BigDecimal("50000"));

    service.reserve(77L, CUSTOMER, new BigDecimal("500000"), priced, NOW);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc, times(2)).update(sql.capture(), params.capture());
    assertThat(sql.getAllValues().get(0)).contains("used_count = used_count + 1");
    assertThat(sql.getAllValues().get(1)).contains("voucher_redemptions").contains("'RESERVED'");
    assertThat(params.getAllValues().get(1).getValue("order")).isEqualTo(77L);
    assertThat(params.getAllValues().get(1).getValue("discount")).isEqualTo(new BigDecimal("50000"));
  }

  @Test
  void reserveFailsWhenTheLockedVoucherIsAlreadyExhaustedAndWritesNothing() {
    doReturn(voucher("PERCENT", "10", "0", null, 1, 1)).when(service).find("SALE10", true);
    doReturn(0).when(service).customerRedemptions(any(), any());
    VoucherQuote priced = new VoucherQuote(9L, "SALE10", new BigDecimal("50000"));

    assertRejected(
        () -> service.reserve(77L, CUSTOMER, new BigDecimal("500000"), priced, NOW),
        ErrorCode.VOUCHER_EXHAUSTED);
    verify(jdbc, times(0)).update(anyString(), any(MapSqlParameterSource.class));
  }

  @Test
  void reserveRefusesWhenTheDiscountChangedSinceTheOrderWasPriced() {
    doReturn(percent("20")).when(service).find("SALE10", true);
    doReturn(0).when(service).customerRedemptions(any(), any());
    VoucherQuote priced = new VoucherQuote(9L, "SALE10", new BigDecimal("50000"));

    assertRejected(
        () -> service.reserve(77L, CUSTOMER, new BigDecimal("500000"), priced, NOW),
        ErrorCode.VOUCHER_CHANGED);
  }

  @Test
  void consumeAndReleaseAreSingleIdempotentStatements() {
    service.consume(77L);
    service.release(77L);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(2)).update(sql.capture(), any(MapSqlParameterSource.class));
    assertThat(sql.getAllValues().get(0)).contains("'CONSUMED'").contains("status = 'RESERVED'");
    assertThat(sql.getAllValues().get(1))
        .contains("'RELEASED'")
        .contains("status = 'RESERVED'")
        .contains("GREATEST(v.used_count - 1, 0)");
  }

  @Test
  void aRefundedOrderGetsItsVoucherUseBackEvenWhenTheUseWasAlreadyConsumed() {
    service.returnAfterRefund(77L);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(MapSqlParameterSource.class));
    assertThat(sql.getValue())
        .contains("status IN ('RESERVED', 'CONSUMED')")
        .contains("'RELEASED'")
        .contains("GREATEST(v.used_count - 1, 0)");
  }
}
