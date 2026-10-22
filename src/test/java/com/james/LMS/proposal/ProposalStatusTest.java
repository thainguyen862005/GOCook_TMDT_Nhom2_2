package com.james.LMS.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProposalStatusTest {

  static final Set<ProposalStatus> FINAL =
      EnumSet.of(
          ProposalStatus.WITHDRAWN,
          ProposalStatus.ACCEPTED,
          ProposalStatus.REJECTED,
          ProposalStatus.EXPIRED);

  @Test
  void draftCanOnlyBeSent() {
    for (ProposalStatus target : ProposalStatus.values()) {
      boolean expected = target == ProposalStatus.DRAFT || target == ProposalStatus.SENT;
      assertThat(ProposalStatus.DRAFT.canTransitionTo(target)).as("DRAFT -> " + target).isEqualTo(expected);
    }
  }

  @Test
  void sentMovesToRevisedOrAnyClosedState() {
    assertThat(ProposalStatus.SENT.canTransitionTo(ProposalStatus.REVISED)).isTrue();
    for (ProposalStatus target : FINAL) {
      assertThat(ProposalStatus.SENT.canTransitionTo(target)).as("SENT -> " + target).isTrue();
    }
    assertThat(ProposalStatus.SENT.canTransitionTo(ProposalStatus.DRAFT)).isFalse();
  }

  @Test
  void revisedGoesBackToSentOrAnyClosedState() {
    assertThat(ProposalStatus.REVISED.canTransitionTo(ProposalStatus.SENT)).isTrue();
    for (ProposalStatus target : FINAL) {
      assertThat(ProposalStatus.REVISED.canTransitionTo(target)).as("REVISED -> " + target).isTrue();
    }
    assertThat(ProposalStatus.REVISED.canTransitionTo(ProposalStatus.DRAFT)).isFalse();
  }

  @Test
  void closedStatesNeverMoveOn() {
    for (ProposalStatus from : FINAL) {
      for (ProposalStatus target : ProposalStatus.values()) {
        if (target != from) {
          assertThat(from.canTransitionTo(target)).as(from + " -> " + target).isFalse();
        }
      }
    }
  }
}
