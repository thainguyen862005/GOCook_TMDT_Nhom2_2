package com.james.LMS.pricing;

import com.james.LMS.config.SecurityUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
@RequiredArgsConstructor
public class PriceGuidanceController {
  private final QuoteGuidanceService service;

  @GetMapping("/api/custom-requests/{requestId}/price-guidance")
  public PriceGuidance guidance(
      @AuthenticationPrincipal SecurityUserDetails actor, @PathVariable Long requestId) {
    return service.guidanceFor(actor.getId(), requestId);
  }
}
