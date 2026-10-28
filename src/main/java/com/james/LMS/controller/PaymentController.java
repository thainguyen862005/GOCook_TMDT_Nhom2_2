package com.james.LMS.controller;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.request.PaymentCheckoutRequest;
import com.james.LMS.request.PaymentOrderCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.response.PaymentStatusResponse;
import com.james.LMS.service.PaymentService;
import com.james.LMS.service.EmailVerificationGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAuthority('ROLE_USER')")
@RequiredArgsConstructor
@Validated
public class PaymentController {
  private final PaymentService service;
  private final EmailVerificationGuard emailVerification;

  @PostMapping("/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentCheckoutResponse checkout(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody PaymentCheckoutRequest request,
      HttpServletRequest http) {
    emailVerification.requireVerifiedCurrentUser();
    return service.checkout(actor.getId(), actor.getUsername(), request, clientIp(http));
  }

  @PostMapping("/checkout/order")
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentCheckoutResponse checkoutOrder(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody PaymentOrderCheckoutRequest request,
      HttpServletRequest http) {
    emailVerification.requireVerifiedCurrentUser();
    return service.checkoutOrder(actor.getId(), request, clientIp(http));
  }

  @PostMapping("/vnpay-return")
  public PaymentStatusResponse vnpayReturn(
      @AuthenticationPrincipal SecurityUserDetails actor, @RequestBody Map<String, String> params) {
    return service.handleVnpayReturn(actor.getId(), params);
  }

  @GetMapping("/status")
  public PaymentStatusResponse status(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam @NotBlank String txnRef,
      HttpServletRequest http) {
    return service.status(actor.getId(), txnRef, clientIp(http));
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
