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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.proposal.TestPorts.FakeRequestPort;
import com.james.LMS.service.PaymentGateway;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ProposalReadServiceTest {
  static final long THIRD_CHEF = 42L;

  final ProposalStore store = new ProposalStore();
  final FakeRequestPort requests = new FakeRequestPort(TestPorts.snapshot());
  final ProposalReadService service =
      new ProposalReadService(store.repository, TestPorts.ports(requests, null), store.chefs);

  static OffsetDateTime in(Duration duration) {
    return OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE).plus(duration);
  }

  Proposal live(long chefId, String price) {
    return store.add(REQUEST_ID, chefId, price, ProposalStatus.SENT, in(Duration.ofDays(2)));
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ProposalException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  List<Long> ids(ProposalList list) {
    return list.items().stream().map(ProposalCard::id).toList();
  }

  @Test
  void aRequestWithoutProposalsGivesAnEmptyList() {
    ProposalList list = service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED);

    assertThat(list.count()).isZero();
    assertThat(list.items()).isEmpty();
    assertThat(list.budgetMax()).isEqualByComparingTo("1500000");
  }

  @Test
  void onlyTheOwnerOfTheRequestMaySeeItsProposals() {
    live(CHEF, "1000000");

    assertRejected(
        () -> service.listForCustomer(99L, REQUEST_ID, ProposalSort.PRICE), ErrorCode.PROPOSAL_FORBIDDEN);
    assertRejected(
        () -> service.listForCustomer(CUSTOMER, 404L, ProposalSort.PRICE), ErrorCode.PROPOSAL_REQUEST_NOT_FOUND);
    assertThat(store.byId).hasSize(1);
  }

  @Test
  void draftsAreHiddenFromTheCustomerButWithdrawnAndRejectedAreShownAsClosed() {
    store.chef(CHEF, "Chef A", "4.0", true).chef(OTHER_CHEF, "Chef B", "4.5", true).chef(THIRD_CHEF, "Chef C", "3.0", true);
    store.add(REQUEST_ID, CHEF, "1000000", ProposalStatus.DRAFT, in(Duration.ofDays(2)));
    Proposal withdrawn = store.add(REQUEST_ID, OTHER_CHEF, "1100000", ProposalStatus.WITHDRAWN, in(Duration.ofDays(2)));
    Proposal rejected = store.add(REQUEST_ID, THIRD_CHEF, "1200000", ProposalStatus.REJECTED, in(Duration.ofDays(2)));

    ProposalList list = service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.PRICE);

    assertThat(ids(list)).containsExactly(withdrawn.getId(), rejected.getId());
    assertThat(list.items()).allSatisfy(card -> assertThat(card.acceptable()).isFalse());
    assertThat(list.items().get(0).status()).isEqualTo(ProposalStatus.WITHDRAWN);
  }

  @Test
  void anOverdueProposalReadsAsExpiredWithoutAnyWriteToTheDatabase() {
    store.chef(CHEF, "Chef A", "4.0", true);
    Proposal overdue = store.add(REQUEST_ID, CHEF, "1000000", ProposalStatus.SENT, in(Duration.ofMinutes(-3)));

    ProposalList list = service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED);

    assertThat(list.items().get(0).status()).isEqualTo(ProposalStatus.EXPIRED);
    assertThat(list.items().get(0).acceptable()).isFalse();
    assertThat(store.statusOf(overdue)).isEqualTo(ProposalStatus.SENT);
    verify(store.repository, never()).saveAll(any());
    verify(store.repository, never()).saveAndFlush(any());
    verify(store.repository, never()).save(any());
  }

  @Test
  void anInactiveChefStaysVisibleButCannotBeAccepted() {
    store.chef(CHEF, "Chef A", "4.0", false);
    live(CHEF, "1000000");

    ProposalCard card = service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED).items().get(0);

    assertThat(card.chef().active()).isFalse();
    assertThat(card.acceptable()).isFalse();
    assertThat(card.status()).isEqualTo(ProposalStatus.SENT);
  }

  @Test
  void budgetDifferenceIsMeasuredAgainstTheBudgetCeiling() {
    store.chef(CHEF, "Chef A", "4.0", true).chef(OTHER_CHEF, "Chef B", "4.0", true);
    Proposal under = live(CHEF, "1200000");
    Proposal over = live(OTHER_CHEF, "1800000");

    ProposalList list = service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.PRICE);
    ProposalCard underCard = list.items().stream().filter(c -> c.id().equals(under.getId())).findFirst().orElseThrow();
    ProposalCard overCard = list.items().stream().filter(c -> c.id().equals(over.getId())).findFirst().orElseThrow();

    assertThat(underCard.budgetDifference().amount()).isEqualByComparingTo("-300000");
    assertThat(underCard.budgetDifference().percent()).isEqualByComparingTo("-20.0");
    assertThat(underCard.budgetDifference().withinBudget()).isTrue();
    assertThat(overCard.budgetDifference().amount()).isEqualByComparingTo("300000");
    assertThat(overCard.budgetDifference().percent()).isEqualByComparingTo("20.0");
    assertThat(overCard.budgetDifference().withinBudget()).isFalse();
  }

  @Test
  void aRequestWithoutBudgetHasNoDifference() {
    store.chef(CHEF, "Chef A", "4.0", true);
    live(CHEF, "1200000");
    requests.requests.put(
        REQUEST_ID,
        new com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot(
            REQUEST_ID, CUSTOMER, true, 4, null, null, Set.of(CHEF), "Mon Viet", null, null, null, null, null, null));

    assertThat(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.PRICE).items().get(0).budgetDifference())
        .isNull();
  }

  @Test
  void sortOrdersAreDeterministicAndPutAcceptableProposalsFirst() {
    store.chef(CHEF, "A", "4.0", true).chef(OTHER_CHEF, "B", "4.8", true).chef(THIRD_CHEF, "C", "5.0", true).chef(43L, "D", "5.0", true);
    Proposal a = live(CHEF, "1000000");
    Proposal b = live(OTHER_CHEF, "1200000");
    Proposal c = live(THIRD_CHEF, "1800000");
    Proposal d = store.add(REQUEST_ID, 43L, "900000", ProposalStatus.SENT, in(Duration.ofMinutes(-1)));

    assertThat(ids(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED)))
        .containsExactly(b.getId(), a.getId(), c.getId(), d.getId());
    assertThat(ids(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.PRICE)))
        .containsExactly(a.getId(), b.getId(), c.getId(), d.getId());
    assertThat(ids(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RATING)))
        .containsExactly(c.getId(), b.getId(), a.getId(), d.getId());
    assertThat(ids(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED)))
        .isEqualTo(ids(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED)));
  }

  @Test
  void equalRatingAndPriceFallBackToTheOldestProposal() {
    store.chef(CHEF, "A", "4.0", true).chef(OTHER_CHEF, "B", "4.0", true);
    Proposal first = live(CHEF, "1000000");
    Proposal second = live(OTHER_CHEF, "1000000");

    assertThat(ids(service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED)))
        .containsExactly(first.getId(), second.getId());
  }

  @Test
  void sortNameIsParsedOrRejected() {
    assertThat(ProposalSort.parse("Price")).isEqualTo(ProposalSort.PRICE);
    assertThat(ProposalSort.parse("recommended")).isEqualTo(ProposalSort.RECOMMENDED);
    assertRejected(() -> ProposalSort.parse("cheapest"), ErrorCode.PROPOSAL_VALIDATION);
  }

  @Test
  void theCardExposesOnlyPublicFields() throws Exception {
    store.chef(CHEF, "Chef A", "4.0", true);
    live(CHEF, "1000000");

    ProposalCard card = service.listForCustomer(CUSTOMER, REQUEST_ID, ProposalSort.RECOMMENDED).items().get(0);
    JsonNode json = Jackson2ObjectMapperBuilder.json().build().valueToTree(card);

    Set<String> keys = new HashSet<>();
    json.fieldNames().forEachRemaining(keys::add);
    assertThat(keys)
        .containsExactlyInAnyOrder(
            "id", "requestId", "chef", "proposedPrice", "budgetDifference", "dishes", "note",
            "expiresAt", "status", "revision", "version", "acceptable", "bookingId", "orderCode");
    Set<String> chefKeys = new HashSet<>();
    json.get("chef").fieldNames().forEachRemaining(chefKeys::add);
    assertThat(chefKeys).containsExactlyInAnyOrder("id", "displayName", "rating", "verified", "active");
    assertThat(new ObjectMapper().writeValueAsString(keys)).doesNotContain("score", "weight", "factor");
  }

  @Test
  void detailIsForTheOwnerOrTheAuthorChefOnly() {
    store.chef(CHEF, "Chef A", "4.0", true);
    Proposal sent = live(CHEF, "1000000");
    Proposal draft = store.add(REQUEST_ID, OTHER_CHEF, "1000000", ProposalStatus.DRAFT, in(Duration.ofDays(2)));

    assertThat(service.get(CUSTOMER, sent.getId()).id()).isEqualTo(sent.getId());
    assertThat(service.get(CHEF, sent.getId()).id()).isEqualTo(sent.getId());
    assertThat(service.get(OTHER_CHEF, draft.getId()).status()).isEqualTo(ProposalStatus.DRAFT);

    assertRejected(() -> service.get(99L, sent.getId()), ErrorCode.PROPOSAL_FORBIDDEN);
    assertRejected(() -> service.get(OTHER_CHEF, sent.getId()), ErrorCode.PROPOSAL_FORBIDDEN);
    assertRejected(() -> service.get(CUSTOMER, draft.getId()), ErrorCode.PROPOSAL_NOT_FOUND);
    assertRejected(() -> service.get(CUSTOMER, 9999L), ErrorCode.PROPOSAL_NOT_FOUND);
  }
}
