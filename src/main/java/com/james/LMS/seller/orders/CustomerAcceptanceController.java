package com.james.LMS.seller.orders;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.payment.escrow.EscrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Only the owner can accept a finished service. EscrowService performs all checks and payout. */
@RestController
@RequestMapping("/api/customer/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CustomerAcceptanceController {
  private final EscrowService escrows;
  @PostMapping("/{id}/accept")
  public java.util.Map<String,String> accept(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long id) {
    return java.util.Map.of("status",escrows.release(id,actor.getId()).name());
  }
}
