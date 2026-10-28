package com.james.LMS.pricing;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@PreAuthorize("hasAuthority('ROLE_USER')")
@RequiredArgsConstructor
public class PricingController {
  private final PricingService service;

  @PostMapping("/fixed-preview")
  public FixedMenuPreview fixedPreview(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody FixedPreviewRequest request) {
    return service.previewFixedMenu(
        request.productId(), request.guestCount(), request.voucherCode(), actor.getId());
  }
}
