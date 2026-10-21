package com.james.LMS.proposal;

import static com.james.LMS.proposal.TestPorts.CHEF;
import static com.james.LMS.proposal.TestPorts.OTHER_CHEF;
import static com.james.LMS.proposal.TestPorts.REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.pricing.MenuPriceReader;
import com.james.LMS.pricing.PriceGuidance.PriceVerdict;
import com.james.LMS.pricing.PricingProperties;
import com.james.LMS.pricing.QuoteGuidanceService;
import com.james.LMS.proposal.PatchProposalRequest.Action;
import com.james.LMS.proposal.TestPorts.FakeRequestPort;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProposalServiceTest {
  final ProposalStore store = new ProposalStore();
  final FakeRequestPort requests = new FakeRequestPort(TestPorts.snapshot());
  final MenuPriceReader menus = mock(MenuPriceReader.class);
  final PricingProperties pricing = new PricingProperties();
  final ProposalProperties properties = new ProposalProperties();
  final com.james.LMS.proposal.port.ProposalPorts ports = TestPorts.ports(requests, null);
  final ProposalService service =
      new ProposalService(
          store.repository,
          ports,
          new ProposalExpiry(store.repository, store.transactions),
          new QuoteGuidanceService(menus, pricing, ports),
          properties,
          store.transactions);

  static OffsetDateTime in(Duration duration) {
    return OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE).plus(duration);
  }

  CreateProposalRequest draft(String price) {
    return new CreateProposalRequest(
        new BigDecimal(price), "Goi cuon; Ca kho to", "note", in(Duration.ofDays(3)), false);
  }

  CreateProposalRequest sent(String price) {
    return new CreateProposalRequest(
        new BigDecimal(price), "Goi cuon; Ca kho to", "note", in(Duration.ofDays(3)), true);
  }

  PatchProposalRequest patch(long version, String price, String note, Action action) {
    return new PatchProposalRequest(
        version, price == null ? null : new BigDecimal(price), null, note, null, action);
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ProposalException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void createKeepsADraftOrSendsRightAway() {
    ProposalView draft = service.create(CHEF, REQUEST_ID, draft("1200000"));
    ProposalView sent = service.create(OTHER_CHEF, REQUEST_ID, sent("1300000"));

    assertThat(draft.status()).isEqualTo(ProposalStatus.DRAFT);
    assertThat(sent.status()).isEqualTo(ProposalStatus.SENT);
    assertThat(draft.revision()).isEqualTo(1);
    assertThat(draft.chefId()).isEqualTo(CHEF);
    assertThat(draft.customRequestId()).isEqualTo(REQUEST_ID);
    assertThat(draft.proposedPrice()).isEqualByComparingTo("1200000");
    assertThat(store.byId).hasSize(2);
  }

  @Test
  void onlyMatchedChefsMayProposeAndOnlyWhileTheRequestIsOpen() {
    assertRejected(() -> service.create(99L, REQUEST_ID, sent("1000000")), ErrorCode.PROPOSAL_CHEF_NOT_MATCHED);

    requests.requests.put(REQUEST_ID, TestPorts.closed(TestPorts.snapshot()));
    assertRejected(() -> service.create(CHEF, REQUEST_ID, sent("1000000")), ErrorCode.PROPOSAL_REQUEST_CLOSED);

    assertRejected(() -> service.create(CHEF, 404L, sent("1000000")), ErrorCode.PROPOSAL_REQUEST_NOT_FOUND);
    assertThat(store.byId).isEmpty();
  }

  @Test
  void priceMustBePositiveAndExpiryMustBeInsideTheWindow() {
    for (String price : List.of("0", "-5")) {
      assertRejected(() -> service.create(CHEF, REQUEST_ID, sent(price)), ErrorCode.PROPOSAL_INVALID_PRICE);
    }
    for (OffsetDateTime expiry :
        new OffsetDateTime[] {null, in(Duration.ofMinutes(-1)), in(Duration.ofDays(15))}) {
      CreateProposalRequest body =
          new CreateProposalRequest(new BigDecimal("1000000"), "d", "n", expiry, true);
      assertRejected(() -> service.create(CHEF, REQUEST_ID, body), ErrorCode.PROPOSAL_INVALID_EXPIRY);
    }
    assertThat(store.byId).isEmpty();
  }

  @Test
  void aChefHasAtMostOneLiveProposalPerRequestUntilItIsWithdrawn() {
    ProposalView first = service.create(CHEF, REQUEST_ID, sent("1200000"));

    assertRejected(() -> service.create(CHEF, REQUEST_ID, draft("1100000")), ErrorCode.PROPOSAL_ALREADY_EXISTS);
    assertThat(service.create(OTHER_CHEF, REQUEST_ID, sent("1250000")).id()).isNotEqualTo(first.id());

    service.update(CHEF, REQUEST_ID, first.id(), patch(first.version(), null, null, Action.WITHDRAW));

    assertThat(service.create(CHEF, REQUEST_ID, sent("1100000")).status()).isEqualTo(ProposalStatus.SENT);
  }

  @Test
  void editingASentProposalMakesItRevisedAndEveryEditBumpsTheRevision() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));

    ProposalView first = service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1100000", null, null));
    ProposalView second = service.update(CHEF, REQUEST_ID, sent.id(), patch(first.version(), null, "new note", null));

    assertThat(first.status()).isEqualTo(ProposalStatus.REVISED);
    assertThat(first.revision()).isEqualTo(2);
    assertThat(first.proposedPrice()).isEqualByComparingTo("1100000");
    assertThat(second.status()).isEqualTo(ProposalStatus.REVISED);
    assertThat(second.revision()).isEqualTo(3);
    assertThat(second.note()).isEqualTo("new note");
  }

  @Test
  void aRevisedProposalCanBeSentAgainAndEditAndSendWorkInOneCall() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));
    ProposalView revised = service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1100000", null, null));

    ProposalView resent = service.update(CHEF, REQUEST_ID, sent.id(), patch(revised.version(), null, null, Action.SEND));
    ProposalView editedAndSent =
        service.update(CHEF, REQUEST_ID, sent.id(), patch(resent.version(), "1000000", null, Action.SEND));

    assertThat(resent.status()).isEqualTo(ProposalStatus.SENT);
    assertThat(editedAndSent.status()).isEqualTo(ProposalStatus.SENT);
    assertThat(editedAndSent.revision()).isEqualTo(3);
    assertThat(editedAndSent.proposedPrice()).isEqualByComparingTo("1000000");
  }

  @Test
  void aDraftIsEditedInPlaceAndSentWithAction() {
    ProposalView draft = service.create(CHEF, REQUEST_ID, draft("1200000"));

    ProposalView edited = service.update(CHEF, REQUEST_ID, draft.id(), patch(draft.version(), "1150000", null, null));
    ProposalView sent = service.update(CHEF, REQUEST_ID, draft.id(), patch(edited.version(), null, null, Action.SEND));

    assertThat(edited.status()).isEqualTo(ProposalStatus.DRAFT);
    assertThat(sent.status()).isEqualTo(ProposalStatus.SENT);
  }

  @Test
  void staleVersionIsRejectedWithoutChangingAnything() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));
    service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1100000", null, null));

    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "900000", null, null)),
        ErrorCode.PROPOSAL_VERSION_CONFLICT);
    assertThat(store.byId.get(sent.id()).getProposedPrice()).isEqualByComparingTo("1100000");
  }

  @Test
  void withdrawMovesToWithdrawnAndClosedProposalsCannotChangeAgain() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));

    ProposalView withdrawn =
        service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), null, null, Action.WITHDRAW));

    assertThat(withdrawn.status()).isEqualTo(ProposalStatus.WITHDRAWN);
    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, sent.id(), patch(withdrawn.version(), "1000000", null, null)),
        ErrorCode.PROPOSAL_INVALID_TRANSITION);
    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, sent.id(), patch(withdrawn.version(), null, null, Action.SEND)),
        ErrorCode.PROPOSAL_INVALID_TRANSITION);
  }

  @Test
  void aDraftCannotBeWithdrawnAndWithdrawCannotCarryEdits() {
    ProposalView draft = service.create(CHEF, REQUEST_ID, draft("1200000"));
    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, draft.id(), patch(draft.version(), null, null, Action.WITHDRAW)),
        ErrorCode.PROPOSAL_INVALID_TRANSITION);

    ProposalView sent = service.create(OTHER_CHEF, REQUEST_ID, sent("1300000"));
    assertRejected(
        () -> service.update(OTHER_CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1000000", null, Action.WITHDRAW)),
        ErrorCode.PROPOSAL_VALIDATION);
    assertRejected(
        () -> service.update(OTHER_CHEF, REQUEST_ID, sent.id(), patch(sent.version(), null, null, null)),
        ErrorCode.PROPOSAL_VALIDATION);
  }

  @Test
  void withdrawStillWorksAfterTheRequestStoppedAcceptingProposals() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));
    requests.requests.put(REQUEST_ID, TestPorts.closed(TestPorts.snapshot()));

    assertThat(service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), null, null, Action.WITHDRAW)).status())
        .isEqualTo(ProposalStatus.WITHDRAWN);
    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version() + 1, "1000000", null, null)),
        ErrorCode.PROPOSAL_INVALID_TRANSITION);
  }

  @Test
  void onlyTheAuthorChefOfThatRequestMayChangeAProposal() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));

    assertRejected(
        () -> service.update(OTHER_CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1", null, null)),
        ErrorCode.PROPOSAL_FORBIDDEN);
    assertRejected(
        () -> service.update(CHEF, 77L, sent.id(), patch(sent.version(), "1", null, null)),
        ErrorCode.PROPOSAL_REQUEST_NOT_FOUND);
    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, 9999L, patch(0, "1", null, null)),
        ErrorCode.PROPOSAL_NOT_FOUND);
  }

  @Test
  void aChefRemovedFromTheMatchCannotEditButTheRequestMustStillBeOpenToSend() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));
    requests.requests.put(
        REQUEST_ID,
        new com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot(
            REQUEST_ID, TestPorts.CUSTOMER, true, 4, null, null, java.util.Set.of(OTHER_CHEF),
            "Mon Viet", null, null, null, null, null, null));

    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1", null, null)),
        ErrorCode.PROPOSAL_CHEF_NOT_MATCHED);
  }

  @Test
  void overdueProposalsAreExpiredLazilyByTheNextWriteOfThatRequestAndTheChangeSurvivesAFailure() {
    ProposalView sent = service.create(CHEF, REQUEST_ID, sent("1200000"));
    Proposal stored = store.byId.get(sent.id());
    ReflectionTestUtils.setField(stored, "expiresAt", in(Duration.ofMinutes(-5)));
    Proposal otherRequest =
        store.add(88L, CHEF, "1000000", ProposalStatus.SENT, in(Duration.ofMinutes(-5)));

    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, sent.id(), patch(sent.version(), "1000000", null, null)),
        ErrorCode.PROPOSAL_EXPIRED);

    assertThat(store.statusOf(stored)).isEqualTo(ProposalStatus.EXPIRED);
    assertThat(store.statusOf(otherRequest)).isEqualTo(ProposalStatus.SENT);
    // the expiry sweep committed on its own while the failed command rolled back
    verify(store.txManager, times(3)).commit(any());
    verify(store.txManager, times(1)).rollback(any());
    assertThat(service.create(CHEF, REQUEST_ID, sent("1100000")).status()).isEqualTo(ProposalStatus.SENT);
  }

  @Test
  void sendingNeedsAFutureExpiry() {
    ProposalView draft = service.create(CHEF, REQUEST_ID, draft("1200000"));
    ReflectionTestUtils.setField(store.byId.get(draft.id()), "expiresAt", in(Duration.ofMinutes(-1)));

    assertRejected(
        () -> service.update(CHEF, REQUEST_ID, draft.id(), patch(draft.version(), null, null, Action.SEND)),
        ErrorCode.PROPOSAL_INVALID_EXPIRY);
  }

  @Test
  void theGuidanceFlagIsInformationOnlyAndNeverBlocksTheChef() {
    when(menus.chefPrices(CHEF, "Mon Viet")).thenReturn(List.of());
    when(menus.marketPrices("Mon Viet"))
        .thenReturn(java.util.stream.Stream.generate(() -> new BigDecimal("100000")).limit(5).toList());

    assertThat(service.create(CHEF, REQUEST_ID, draft("400000")).priceGuidance()).isEqualTo(PriceVerdict.IN_RANGE);
    assertThat(service.create(OTHER_CHEF, REQUEST_ID, draft("100000")).priceGuidance()).isEqualTo(PriceVerdict.BELOW);
    assertThat(service.create(42L, REQUEST_ID, draft("900000")).priceGuidance()).isEqualTo(PriceVerdict.ABOVE);
  }

  @Test
  void withoutEnoughMenusThereIsNoGuidanceButTheProposalIsStillSaved() {
    ProposalView created = service.create(CHEF, REQUEST_ID, sent("1200000"));

    assertThat(created.priceGuidance()).isEqualTo(PriceVerdict.NO_GUIDANCE);
    assertThat(created.status()).isEqualTo(ProposalStatus.SENT);
  }
}
