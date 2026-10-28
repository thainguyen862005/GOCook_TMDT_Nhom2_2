package com.james.LMS.proposal.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Read-only view of a customer's custom request, implemented by the custom request module (Thái).
 *
 * <p>Semantics: {@link #load(Long)} returns the current state of the request, or throws {@link
 * com.james.LMS.proposal.ProposalException#requestNotFound(Long)} (HTTP 404) when the id does not
 * exist. It must not modify anything. It is called inside the caller's transaction.
 */
public interface ProposalRequestPort {

  RequestSnapshot load(Long requestId);

  /**
   * Required fields are never null: requestId, customerId, guestCount (>= 1), matchedChefIds (empty
   * set when nobody is matched). budgetMin and budgetMax are always present as components but are
   * null when the customer did not give that bound. Optional fields are null when unknown and only
   * improve the price guidance.
   *
   * @param acceptingProposals true while chefs may still send or edit proposals
   * @param matchedChefIds users.id of the chefs allowed to propose
   */
  record RequestSnapshot(
      Long requestId,
      Long customerId,
      boolean acceptingProposals,
      int guestCount,
      BigDecimal budgetMin,
      BigDecimal budgetMax,
      Set<Long> matchedChefIds,
      String cuisine,
      Integer dishCount,
      Set<String> dietaryTags,
      Set<String> allergyTags,
      String serviceArea,
      Integer durationMinutes,
      LocalDateTime scheduledAt) {}
}
