package com.james.LMS.proposal;

import static com.james.LMS.proposal.TestPorts.CHEF;
import static com.james.LMS.proposal.TestPorts.CUSTOMER;
import static com.james.LMS.proposal.TestPorts.OTHER_CHEF;
import static com.james.LMS.proposal.TestPorts.REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.proposal.TestPorts.FakeBookingPort;
import com.james.LMS.proposal.TestPorts.FakeRequestPort;
import com.james.LMS.proposal.port.BookingPort.BookingCommand;
import com.james.LMS.proposal.port.BookingUnavailableException;
import com.james.LMS.service.PaymentGateway;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ProposalAcceptServiceTest {
  static final long THIRD_CHEF = 42L;

  final ProposalStore store = new ProposalStore();
  final FakeRequestPort requests = new FakeRequestPort(TestPorts.snapshot());
  final FakeBookingPort bookings = new FakeBookingPort();
  final ProposalAcceptService service = serviceWith(TestPorts.ports(requests, bookings));

  ProposalAcceptService serviceWith(com.james.LMS.proposal.port.ProposalPorts ports) {
    return new ProposalAcceptService(
        store.repository,
        ports,
        new ProposalExpiry(store.repository, store.transactions),
        store.chefs,
        store.transactions);
  }

  static OffsetDateTime in(Duration duration) {
    return OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE).plus(duration);
  }

  Proposal sent(long chefId, String price) {
    return store.add(REQUEST_ID, chefId, price, ProposalStatus.SENT, in(Duration.ofDays(2)));
  }

  void activeChefs() {
    store.chef(CHEF, "Chef A", "4.0", true).chef(OTHER_CHEF, "Chef B", "4.5", true).chef(THIRD_CHEF, "Chef C", "3.0", true);
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ProposalException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void acceptingOneProposalRejectsTheOtherLiveOnesAndBooksOnce() {
    activeChefs();
    Proposal winner = sent(CHEF, "1200000");
    Proposal sentLoser = sent(OTHER_CHEF, "1000000");
    Proposal revisedLoser =
        store.add(REQUEST_ID, THIRD_CHEF, "1100000", ProposalStatus.REVISED, in(Duration.ofDays(2)));
    Proposal withdrawn =
        store.add(REQUEST_ID, 43L, "900000", ProposalStatus.WITHDRAWN, in(Duration.ofDays(2)));
    Proposal draft = store.add(REQUEST_ID, 44L, "800000", ProposalStatus.DRAFT, in(Duration.ofDays(2)));

    AcceptResult result = service.accept(CUSTOMER, winner.getId());

    assertThat(result.proposalId()).isEqualTo(winner.getId());
    assertThat(result.totalAmount()).isEqualByComparingTo("1200000");
    assertThat(result.bookingId()).isEqualTo(bookings.created.get(winner.getId()).bookingId());
    assertThat(result.orderCode()).isEqualTo(bookings.created.get(winner.getId()).orderCode());
    assertThat(store.statusOf(winner)).isEqualTo(ProposalStatus.ACCEPTED);
    assertThat(winner.getAcceptedBookingId()).isEqualTo(result.bookingId());
    assertThat(winner.getAcceptedOrderCode()).isEqualTo(result.orderCode());
    assertThat(store.statusOf(sentLoser)).isEqualTo(ProposalStatus.REJECTED);
    assertThat(store.statusOf(revisedLoser)).isEqualTo(ProposalStatus.REJECTED);
    assertThat(store.statusOf(withdrawn)).isEqualTo(ProposalStatus.WITHDRAWN);
    assertThat(store.statusOf(draft)).isEqualTo(ProposalStatus.DRAFT);

    BookingCommand sentToBooking = bookings.calls.get(0);
    assertThat(bookings.calls).hasSize(1);
    assertThat(sentToBooking.proposalId()).isEqualTo(winner.getId());
    assertThat(sentToBooking.requestId()).isEqualTo(REQUEST_ID);
    assertThat(sentToBooking.customerId()).isEqualTo(CUSTOMER);
    assertThat(sentToBooking.chefId()).isEqualTo(CHEF);
    assertThat(sentToBooking.agreedPrice()).isEqualByComparingTo("1200000");
    assertThat(sentToBooking.dishes()).isEqualTo("Goi cuon; Ca kho to");
    assertThat(sentToBooking.guestCount()).isEqualTo(4);
  }

  @Test
  void acceptingTwiceReturnsTheSameResultAndDoesNotBookAgain() {
    activeChefs();
    Proposal winner = sent(CHEF, "1200000");
    sent(OTHER_CHEF, "1000000");

    AcceptResult first = service.accept(CUSTOMER, winner.getId());
    AcceptResult second = service.accept(CUSTOMER, winner.getId());

    assertThat(second).isEqualTo(first);
    assertThat(bookings.calls).hasSize(1);
  }

  @Test
  void acceptingAnotherProposalAfterAWinnerExistsIsAConflict() {
    activeChefs();
    Proposal winner = sent(CHEF, "1200000");
    Proposal other = sent(OTHER_CHEF, "1000000");
    service.accept(CUSTOMER, winner.getId());

    assertRejected(() -> service.accept(CUSTOMER, other.getId()), ErrorCode.PROPOSAL_ALREADY_RESOLVED);
    assertThat(bookings.calls).hasSize(1);
    assertThat(store.statusOf(winner)).isEqualTo(ProposalStatus.ACCEPTED);
  }

  @Test
  void bookingSlotTakenRollsEverythingBackAndKeepsProposalsUntouched() {
    activeChefs();
    Proposal target = sent(CHEF, "1200000");
    Proposal other = sent(OTHER_CHEF, "1000000");
    bookings.failure = new BookingUnavailableException("slot taken");

    assertRejected(() -> service.accept(CUSTOMER, target.getId()), ErrorCode.PROPOSAL_BOOKING_UNAVAILABLE);

    assertThat(store.statusOf(target)).isEqualTo(ProposalStatus.SENT);
    assertThat(store.statusOf(other)).isEqualTo(ProposalStatus.SENT);
    assertThat(target.getAcceptedBookingId()).isNull();
    verify(store.txManager).rollback(any());
  }

  @Test
  void anyOtherBookingFailureAlsoRollsBackAndSurfaces() {
    activeChefs();
    Proposal target = sent(CHEF, "1200000");
    bookings.failure = new IllegalStateException("database is down");

    assertThatThrownBy(() -> service.accept(CUSTOMER, target.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database is down");

    assertThat(store.statusOf(target)).isEqualTo(ProposalStatus.SENT);
    verify(store.txManager).rollback(any());
  }

  @Test
  void aFailedAcceptCanBeRetriedAfterTheSlotFreesUp() {
    activeChefs();
    Proposal target = sent(CHEF, "1200000");
    bookings.failure = new BookingUnavailableException("slot taken");
    assertRejected(() -> service.accept(CUSTOMER, target.getId()), ErrorCode.PROPOSAL_BOOKING_UNAVAILABLE);

    bookings.failure = null;

    assertThat(service.accept(CUSTOMER, target.getId()).bookingId()).isNotNull();
    assertThat(store.statusOf(target)).isEqualTo(ProposalStatus.ACCEPTED);
  }

  @Test
  void anExpiredProposalCannotBeAcceptedAndIsSavedAsExpired() {
    activeChefs();
    Proposal overdue = store.add(REQUEST_ID, CHEF, "1200000", ProposalStatus.SENT, in(Duration.ofMinutes(-2)));

    assertRejected(() -> service.accept(CUSTOMER, overdue.getId()), ErrorCode.PROPOSAL_EXPIRED);

    assertThat(store.statusOf(overdue)).isEqualTo(ProposalStatus.EXPIRED);
    assertThat(bookings.calls).isEmpty();
  }

  @Test
  void onlyTheOwnerOfTheRequestCanAccept() {
    activeChefs();
    Proposal target = sent(CHEF, "1200000");

    assertRejected(() -> service.accept(99L, target.getId()), ErrorCode.PROPOSAL_FORBIDDEN);
    assertRejected(() -> service.accept(CUSTOMER, 9999L), ErrorCode.PROPOSAL_NOT_FOUND);

    assertThat(store.statusOf(target)).isEqualTo(ProposalStatus.SENT);
    assertThat(bookings.calls).isEmpty();
  }

  @Test
  void anotherCustomerCannotReplayTheAcceptEither() {
    activeChefs();
    Proposal winner = sent(CHEF, "1200000");
    service.accept(CUSTOMER, winner.getId());

    assertRejected(() -> service.accept(99L, winner.getId()), ErrorCode.PROPOSAL_FORBIDDEN);
    assertThat(bookings.calls).hasSize(1);
  }

  @Test
  void aProposalOfAnInactiveChefCannotBeAccepted() {
    store.chef(CHEF, "Chef A", "4.0", false);
    Proposal target = sent(CHEF, "1200000");

    assertRejected(() -> service.accept(CUSTOMER, target.getId()), ErrorCode.PROPOSAL_CHEF_INACTIVE);

    assertThat(bookings.calls).isEmpty();
    assertThat(store.statusOf(target)).isEqualTo(ProposalStatus.SENT);
  }

  @Test
  void draftsAndClosedProposalsCannotBeAccepted() {
    activeChefs();
    Proposal draft = store.add(REQUEST_ID, CHEF, "1200000", ProposalStatus.DRAFT, in(Duration.ofDays(2)));
    Proposal withdrawn = store.add(REQUEST_ID, OTHER_CHEF, "1000000", ProposalStatus.WITHDRAWN, in(Duration.ofDays(2)));

    assertRejected(() -> service.accept(CUSTOMER, draft.getId()), ErrorCode.PROPOSAL_INVALID_TRANSITION);
    assertRejected(() -> service.accept(CUSTOMER, withdrawn.getId()), ErrorCode.PROPOSAL_INVALID_TRANSITION);
    assertThat(bookings.calls).isEmpty();
  }

  @Test
  void withoutTheBookingPortTheAcceptFailsClearlyAndChangesNothing() {
    activeChefs();
    Proposal target = sent(CHEF, "1200000");
    ProposalAcceptService withoutBooking = serviceWith(TestPorts.ports(requests, null));

    assertThatThrownBy(() -> withoutBooking.accept(CUSTOMER, target.getId()))
        .isInstanceOfSatisfying(
            ProposalException.class,
            error -> {
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_PORT_NOT_INTEGRATED);
              assertThat(error.getStatus().value()).isEqualTo(501);
              assertThat(error.getMessage()).contains("BookingPort");
            });
    assertThat(store.statusOf(target)).isEqualTo(ProposalStatus.SENT);
    verify(store.txManager).rollback(any());
  }

  @Test
  void acceptingNeverUsesTheOptimisticPathItLocksTheRequestsProposals() {
    activeChefs();
    Proposal target = sent(CHEF, "1200000");

    service.accept(CUSTOMER, target.getId());

    verify(store.repository).lockByCustomRequestId(REQUEST_ID);
    verify(store.repository, never()).save(any());
  }
}
