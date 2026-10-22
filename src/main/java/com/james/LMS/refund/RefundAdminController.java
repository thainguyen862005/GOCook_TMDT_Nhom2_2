package com.james.LMS.refund;

import com.james.LMS.payment.adminread.PaymentAdminReadService;
import com.james.LMS.payment.adminread.PaymentAdminReadService.RefundView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin buttons that move refund money: run a refund request the admin approved, and retry a
 * refund the gateway failed. Both answer with the same {@link RefundView} the admin read endpoints
 * return. Coded errors come from {@code PaymentReadExceptionHandler}.
 */
@RestController
@RequestMapping("/api/admin/payment-refunds")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class RefundAdminController {
  private final RefundService refundService;
  private final PaymentAdminReadService readService;

  @PostMapping("/execute/{adminRequestId}")
  public RefundView execute(@PathVariable Long adminRequestId) {
    return readService.refund(refundService.executeApproved(adminRequestId).id());
  }

  @PostMapping("/{refundId}/retry")
  public RefundView retry(@PathVariable Long refundId) {
    return readService.refund(refundService.retry(refundId).id());
  }
}
