package com.james.LMS.proposal;

import com.james.LMS.proposal.ChefSummaryReader.ChefSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One proposal as shown to the customer who owns the request (and to the chef who wrote it). Only
 * public facts: no matching score, ranking or price-guidance data.
 *
 * @param budgetDifference null when the request has no budget
 * @param acceptable live, not expired, and the chef is still active
 * @param bookingId set once accepted
 */
public record ProposalCard(
    Long id,
    Long requestId,
    ChefSummary chef,
    BigDecimal proposedPrice,
    BudgetDifference budgetDifference,
    String dishes,
    String note,
    OffsetDateTime expiresAt,
    ProposalStatus status,
    int revision,
    long version,
    boolean acceptable,
    Long bookingId,
    String orderCode) {

  /**
   * Distance from the customer's budget ceiling (budgetMax, or budgetMin when only that is given).
   * Positive amount means over budget.
   */
  public record BudgetDifference(BigDecimal amount, BigDecimal percent, boolean withinBudget) {}
}
