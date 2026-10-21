package com.james.LMS.proposal.port;

/** The slot for the proposal's booking is no longer free. Unchecked so the transaction rolls back. */
public class BookingUnavailableException extends RuntimeException {
  public BookingUnavailableException(String message) {
    super(message);
  }
}
