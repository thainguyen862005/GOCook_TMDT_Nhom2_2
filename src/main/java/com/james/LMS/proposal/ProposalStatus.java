package com.james.LMS.proposal;

public enum ProposalStatus {
  DRAFT,
  SENT,
  REVISED,
  WITHDRAWN,
  ACCEPTED,
  REJECTED,
  EXPIRED;

  public boolean canTransitionTo(ProposalStatus target) {
    if (target == null || target == this) {
      return true;
    }

    return switch (this) {
      case DRAFT -> target == SENT;

      case SENT ->
          target == REVISED
              || target == WITHDRAWN
              || target == ACCEPTED
              || target == REJECTED
              || target == EXPIRED;

      case REVISED ->
          target == SENT
              || target == WITHDRAWN
              || target == ACCEPTED
              || target == REJECTED
              || target == EXPIRED;

      case WITHDRAWN, ACCEPTED, REJECTED, EXPIRED -> false;
    };
  }
}
