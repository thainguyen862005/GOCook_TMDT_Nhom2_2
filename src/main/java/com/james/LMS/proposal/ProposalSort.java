package com.james.LMS.proposal;

import com.james.LMS.enums.ErrorCode;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Every order puts proposals the customer can still accept first, then:
 *
 * <ul>
 *   <li>RECOMMENDED: within budget first, then chef rating high to low, then price low to high
 *   <li>PRICE: price low to high
 *   <li>RATING: chef rating high to low, then price low to high
 * </ul>
 *
 * Proposal id (oldest first) breaks every remaining tie, so the order is always the same.
 */
public enum ProposalSort {
  RECOMMENDED,
  PRICE,
  RATING;

  public static ProposalSort parse(String value) {
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ProposalException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.PROPOSAL_VALIDATION,
          "sort phải là recommended, price hoặc rating.");
    }
  }
}
