package com.james.LMS.payment.finance;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.payment.finance.SellerFinanceService.MonthlyIncome;
import com.james.LMS.payment.finance.SellerFinanceService.Summary;
import com.james.LMS.payment.finance.SellerFinanceService.TransactionPage;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A chef sees only their own money: the chef id is the logged-in user, never a parameter. */
@RestController
@RequestMapping("/api/seller/finance")
@PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
@RequiredArgsConstructor
public class SellerFinanceController {
  private final SellerFinanceService service;

  @GetMapping("/summary")
  public Summary summary(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.summary(actor.getId(), FinanceRange.of(from, to));
  }

  @GetMapping("/monthly")
  public List<MonthlyIncome> monthly(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam(defaultValue = "6") int months) {
    return service.monthly(actor.getId(), months);
  }

  @GetMapping("/transactions")
  public TransactionPage transactions(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.transactions(actor.getId(), page, size, status, FinanceRange.of(from, to));
  }
}
