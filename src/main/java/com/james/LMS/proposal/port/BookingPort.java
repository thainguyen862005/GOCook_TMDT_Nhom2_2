package com.james.LMS.proposal.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Creates the booking (order awaiting payment) for an accepted proposal. Implemented by the booking
 * module (Thái). Contract C-09.
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>Idempotent by {@code proposalId}: a second call returns the booking created by the first.
 *   <li>Runs inside the caller's transaction (do not open a new one). Any exception rolls back the
 *       whole accept, so write nothing that must survive a failure.
 *   <li>Throws {@link BookingUnavailableException} when the slot is already taken.
 *   <li>Moves the custom request to its "accepted" state in the same transaction.
 *   <li>The booking total must equal {@code agreedPrice}.
 * </ul>
 */
public interface BookingPort {

  BookingRef createFromProposal(BookingCommand command);

  /**
   * @param agreedPrice final price of the proposal, whole VND
   * @param dishes free text from the proposal
   * @param scheduledAt copied from the request snapshot, may be null when the request module keeps
   *     it elsewhere
   */
  record BookingCommand(
      Long proposalId,
      Long requestId,
      Long customerId,
      Long chefId,
      BigDecimal agreedPrice,
      String dishes,
      int guestCount,
      LocalDateTime scheduledAt) {}

  record BookingRef(Long bookingId, String orderCode) {}
}
