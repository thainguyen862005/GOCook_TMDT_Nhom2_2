package com.james.LMS.proposal;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.service.EmailVerificationGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProposalController {
  private final ProposalService commands;
  private final ProposalReadService reads;
  private final ProposalAcceptService acceptance;
  private final EmailVerificationGuard emailVerification;

  @PostMapping("/api/custom-requests/{requestId}/proposals")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  @ResponseStatus(HttpStatus.CREATED)
  public ProposalView create(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable Long requestId,
      @Valid @RequestBody CreateProposalRequest body) {
    return commands.create(actor.getId(), requestId, body);
  }

  @PatchMapping("/api/custom-requests/{requestId}/proposals/{proposalId}")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  public ProposalView update(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable Long requestId,
      @PathVariable Long proposalId,
      @Valid @RequestBody PatchProposalRequest body) {
    return commands.update(actor.getId(), requestId, proposalId, body);
  }

  @GetMapping("/api/custom-requests/{requestId}/proposals")
  @PreAuthorize("hasAuthority('ROLE_USER')")
  public ProposalList list(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable Long requestId,
      @RequestParam(defaultValue = "recommended") String sort) {
    return reads.listForCustomer(actor.getId(), requestId, ProposalSort.parse(sort));
  }

  @GetMapping("/api/proposals/{id}")
  @PreAuthorize("isAuthenticated()")
  public ProposalCard get(@AuthenticationPrincipal SecurityUserDetails actor, @PathVariable Long id) {
    return reads.get(actor.getId(), id);
  }

  @PostMapping("/api/proposals/{id}/accept")
  @PreAuthorize("hasAuthority('ROLE_USER')")
  public AcceptResult accept(
      @AuthenticationPrincipal SecurityUserDetails actor, @PathVariable Long id) {
    emailVerification.requireVerifiedCurrentUser();
    return acceptance.accept(actor.getId(), id);
  }
}
