package com.james.LMS.proposal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Partial update. {@code version} is the version the chef last saw. Content fields are optional;
 * {@code action} SEND sends (or re-sends) the proposal, WITHDRAW withdraws it and cannot be
 * combined with content changes.
 */
public record PatchProposalRequest(
    @NotNull Long version,
    BigDecimal proposedPrice,
    @Size(max = 4000) String dishes,
    @Size(max = 4000) String note,
    OffsetDateTime expiresAt,
    Action action) {

  public enum Action {
    SEND,
    WITHDRAW
  }

  boolean changesContent() {
    return proposedPrice != null || dishes != null || note != null || expiresAt != null;
  }
}
