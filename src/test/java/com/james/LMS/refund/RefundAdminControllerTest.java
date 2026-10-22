package com.james.LMS.refund;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.james.LMS.config.SecurityConfig;
import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.PaymentReadExceptionHandler;
import com.james.LMS.payment.adminread.PaymentAdminReadService;
import com.james.LMS.payment.adminread.PaymentAdminReadService.RefundView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = RefundAdminController.class,
    excludeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@Import({RefundAdminControllerTest.Security.class, PaymentReadExceptionHandler.class})
class RefundAdminControllerTest {
  static final String EXECUTE = "/api/admin/payment-refunds/execute/77";
  static final String RETRY = "/api/admin/payment-refunds/900/retry";

  @Autowired MockMvc mvc;
  @MockitoBean RefundService refunds;
  @MockitoBean PaymentAdminReadService reads;

  SecurityUserDetails withRole(String role) {
    return SecurityUserDetails.builder()
        .id(1L)
        .email("someone@example.invalid")
        .authorities(List.of(new SimpleGrantedAuthority(role)))
        .build();
  }

  Refund refund(RefundStatus status) {
    OffsetDateTime at = OffsetDateTime.of(2026, 10, 22, 10, 0, 0, 0, ZoneOffset.ofHours(7));
    return new Refund(
        900L,
        8L,
        21L,
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        100,
        RefundReason.ADMIN_APPROVED,
        status,
        "SIM-900",
        null,
        77L,
        at,
        at);
  }

  RefundView view(String status) {
    return new RefundView(
        900L,
        8L,
        "GC261022ABCDE1",
        21L,
        new BigDecimal("1000000"),
        BigDecimal.ZERO,
        100,
        "ADMIN_APPROVED",
        status,
        "SIM-900",
        null,
        77L,
        "2026-10-22T10:00:00+07:00",
        "2026-10-22T10:00:00+07:00");
  }

  PaymentException refused(HttpStatus status, ErrorCode code) {
    return new PaymentException(status, code, "refused");
  }

  @Test
  void onlyASystemAdminMayExecuteOrRetry() throws Exception {
    mvc.perform(post(EXECUTE)).andExpect(status().isUnauthorized());
    mvc.perform(post(RETRY)).andExpect(status().isUnauthorized());
    for (String role : List.of("ROLE_USER", "ROLE_INSTRUCTOR")) {
      mvc.perform(post(EXECUTE).with(user(withRole(role)))).andExpect(status().isForbidden());
      mvc.perform(post(RETRY).with(user(withRole(role)))).andExpect(status().isForbidden());
    }
    verifyNoInteractions(refunds, reads);
  }

  @Test
  void executeAnswersWithTheAdminRefundViewAndNothingFromTheGateway() throws Exception {
    when(refunds.executeApproved(77L)).thenReturn(refund(RefundStatus.REFUNDED));
    when(reads.refund(900L)).thenReturn(view("REFUNDED"));

    mvc.perform(post(EXECUTE).with(user(withRole("ROLE_SYSTEM_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(900))
        .andExpect(jsonPath("$.orderCode").value("GC261022ABCDE1"))
        .andExpect(jsonPath("$.status").value("REFUNDED"))
        .andExpect(jsonPath("$.adminRefundRequestId").value(77))
        .andExpect(jsonPath("$.createdAt").value("2026-10-22T10:00:00+07:00"))
        .andExpect(jsonPath("$.rawReference").doesNotExist())
        .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
  }

  @Test
  void executingTheSameApprovedRequestTwiceReturnsTheSameRefund() throws Exception {
    when(refunds.executeApproved(77L)).thenReturn(refund(RefundStatus.REFUNDED));
    when(reads.refund(900L)).thenReturn(view("REFUNDED"));

    String first =
        mvc.perform(post(EXECUTE).with(user(withRole("ROLE_SYSTEM_ADMIN"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mvc.perform(post(EXECUTE).with(user(withRole("ROLE_SYSTEM_ADMIN"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    verify(refunds, times(2)).executeApproved(77L);
  }

  @Test
  void aRequestThatIsNotApprovedIsRefusedWithItsCode() throws Exception {
    when(refunds.executeApproved(77L))
        .thenThrow(refused(HttpStatus.CONFLICT, ErrorCode.REFUND_ADMIN_REQUEST_INVALID));

    mvc.perform(post(EXECUTE).with(user(withRole("ROLE_SYSTEM_ADMIN"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value("1149"));
    verifyNoInteractions(reads);
  }

  @Test
  void retryReturnsTheRefundAfterTheSecondAttempt() throws Exception {
    when(refunds.retry(900L)).thenReturn(refund(RefundStatus.REFUNDED));
    when(reads.refund(900L)).thenReturn(view("REFUNDED"));

    mvc.perform(post(RETRY).with(user(withRole("ROLE_SYSTEM_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(900))
        .andExpect(jsonPath("$.status").value("REFUNDED"));
  }

  @Test
  void retryOfARefundThatDidNotFailOrDoesNotExistIsRefusedWithItsCode() throws Exception {
    when(refunds.retry(900L)).thenThrow(refused(HttpStatus.CONFLICT, ErrorCode.REFUND_NOT_RETRYABLE));
    when(refunds.retry(404L)).thenThrow(refused(HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND));

    mvc.perform(post(RETRY).with(user(withRole("ROLE_SYSTEM_ADMIN"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("1148"));
    mvc.perform(
            post("/api/admin/payment-refunds/404/retry").with(user(withRole("ROLE_SYSTEM_ADMIN"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("1145"));
    verifyNoInteractions(reads);
  }

  @TestConfiguration
  @EnableMethodSecurity
  static class Security {
    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .httpBasic(Customizer.withDefaults())
          .build();
    }
  }
}
