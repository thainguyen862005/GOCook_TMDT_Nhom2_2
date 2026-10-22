package com.james.LMS.payment.adminread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.adminread.PaymentAdminReadService.EscrowView;
import com.james.LMS.payment.adminread.PaymentAdminReadService.Page;
import com.james.LMS.payment.adminread.PaymentAdminReadService.PaymentDetail;
import com.james.LMS.payment.adminread.PaymentAdminReadService.PaymentView;
import com.james.LMS.payment.adminread.PaymentAdminReadService.Reconciliation;
import com.james.LMS.payment.adminread.PaymentAdminReadService.RefundView;
import com.james.LMS.refund.RefundProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class PaymentAdminReadServiceTest {
  final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  final PaymentAdminReadService service = new PaymentAdminReadService(jdbc, new RefundProperties());

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void thePageSizeIsCappedAtOneHundred() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

    Page<PaymentView> page = service.payments(null, null, null, null, null, null, 0, 5000);

    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).query(anyString(), params.capture(), any(RowMapper.class));
    assertThat(page.size()).isEqualTo(100);
    assertThat(params.getValue().getValue("limit")).isEqualTo(100);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void anUnknownTransactionReferenceGivesAnEmptyPageNotAnError() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

    Page<PaymentView> page = service.payments(null, null, null, "NO-SUCH-TXN", null, null, 0, 20);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void filtersAreBoundAsParametersNeverPastedIntoTheSql() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

    service.payments("success", "vnpay", "GC1", "x'; drop table orders; --", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, 20);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).query(sql.capture(), params.capture(), any(RowMapper.class));
    assertThat(sql.getValue()).doesNotContain("drop table").contains(":txnRef").contains(":status");
    assertThat(params.getValue().getValue("status")).isEqualTo("SUCCESS");
    assertThat(params.getValue().getValue("provider")).isEqualTo("VNPAY");
    assertThat(params.getValue().getValue("txnRef")).isEqualTo("x'; drop table orders; --");
  }

  @Test
  void badFiltersAreCodedBadRequests() {
    assertRejected(() -> service.payments("BOGUS", null, null, null, null, null, 0, 20), ErrorCode.ADMIN_PAYMENT_INVALID_PARAM);
    assertRejected(() -> service.payments(null, "PAYPAL", null, null, null, null, 0, 20), ErrorCode.ADMIN_PAYMENT_INVALID_PARAM);
    assertRejected(() -> service.payments(null, null, null, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), 0, 20), ErrorCode.ADMIN_PAYMENT_INVALID_PARAM);
    assertRejected(() -> service.payments(null, null, null, null, null, null, -1, 20), ErrorCode.ADMIN_PAYMENT_INVALID_PARAM);
    assertRejected(() -> service.refunds("BOGUS", null, 0, 20), ErrorCode.ADMIN_PAYMENT_INVALID_PARAM);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void anUnknownPaymentIdIsNotFound() {
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

    assertRejected(() -> service.payment(404L), ErrorCode.ADMIN_PAYMENT_NOT_FOUND);
  }

  @Test
  void theJsonOfEveryDtoHasNoRawReferenceNoIdempotencyKeyAndNoSignature() throws Exception {
    PaymentView payment =
        new PaymentView(
            1L, 2L, "GC1", "VNPAY", "GC1A2", new BigDecimal("1000000"), "SUCCESS", 2, null,
            "2026-10-22T10:00:00+07:00", "2026-10-22T09:50:00+07:00", "2026-10-22T09:40:00+07:00");
    EscrowView escrow =
        new EscrowView("HELD", new BigDecimal("1000000"), new BigDecimal("150000"), new BigDecimal("850000"),
            "2026-10-22T09:50:00+07:00", null);
    RefundView refund =
        new RefundView(3L, 2L, "GC1", 1L, new BigDecimal("500000"), new BigDecimal("500000"), 50,
            "CUSTOMER_CANCEL", "REFUNDED", "SIM-3", null, null, "2026-10-22T10:00:00+07:00", "2026-10-22T10:00:00+07:00");
    Reconciliation reconciliation = new Reconciliation(List.of(), List.of(), List.of(), List.of(), "2026-10-22T10:00:00+07:00");

    String json =
        new ObjectMapper()
            .writeValueAsString(List.of(new PaymentDetail(payment, escrow, List.of(refund)), reconciliation, payment, refund))
            .toLowerCase();

    assertThat(json).doesNotContain("raw").doesNotContain("secure").doesNotContain("idempotency").doesNotContain("hash");
    assertThat(json).contains("\"txnref\":\"gc1a2\"").contains("+07:00");
  }

  @Test
  void theViewsDeclareNoFieldThatCouldCarryGatewayInternals() {
    for (Class<?> type :
        new Class<?>[] {PaymentView.class, EscrowView.class, RefundView.class, PaymentDetail.class, Reconciliation.class}) {
      for (var component : type.getRecordComponents()) {
        String name = component.getName().toLowerCase();
        assertThat(name).doesNotContain("raw").doesNotContain("secure").doesNotContain("idempotency");
      }
    }
  }
}
