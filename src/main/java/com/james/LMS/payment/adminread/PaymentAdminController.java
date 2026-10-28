package com.james.LMS.payment.adminread;

import com.james.LMS.payment.adminread.PaymentAdminReadService.Page;
import com.james.LMS.payment.adminread.PaymentAdminReadService.PaymentDetail;
import com.james.LMS.payment.adminread.PaymentAdminReadService.PaymentView;
import com.james.LMS.payment.adminread.PaymentAdminReadService.Reconciliation;
import com.james.LMS.payment.adminread.PaymentAdminReadService.RefundView;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class PaymentAdminController {
  private final PaymentAdminReadService service;

  @GetMapping("/payments")
  public Page<PaymentView> payments(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String orderCode,
      @RequestParam(required = false) String txnRef,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.payments(status, provider, orderCode, txnRef, from, to, page, size);
  }

  @GetMapping("/payments/reconciliation")
  public Reconciliation reconciliation() {
    return service.reconciliation();
  }

  @GetMapping("/payments/{id}")
  public PaymentDetail payment(@PathVariable Long id) {
    return service.payment(id);
  }

  @GetMapping("/refunds")
  public Page<RefundView> refunds(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String orderCode,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.refunds(status, orderCode, page, size);
  }
}
