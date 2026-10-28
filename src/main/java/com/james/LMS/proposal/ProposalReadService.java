package com.james.LMS.proposal;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.proposal.ChefSummaryReader.ChefSummary;
import com.james.LMS.proposal.ProposalCard.BudgetDifference;
import com.james.LMS.proposal.port.ProposalPorts;
import com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Read side. Never writes: expiry is computed on the fly, see {@link Proposal#statusAt}. */
@Service
@RequiredArgsConstructor
public class ProposalReadService {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final ProposalRepository proposals;
  private final ProposalPorts ports;
  private final ChefSummaryReader chefs;

  public ProposalList listForCustomer(Long customerId, Long requestId, ProposalSort sort) {
    RequestSnapshot request = ports.requests().load(requestId);
    if (!request.customerId().equals(customerId)) {
      throw forbidden();
    }
    List<Proposal> visible =
        proposals.findByCustomRequestIdOrderById(requestId).stream()
            .filter(proposal -> proposal.getStatus() != ProposalStatus.DRAFT)
            .toList();
    Map<Long, ChefSummary> summaries = chefsOf(visible);
    OffsetDateTime now = OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE);

    List<ProposalCard> items =
        visible.stream()
            .map(proposal -> card(proposal, request, summaries, now))
            .sorted(order(sort))
            .toList();
    return new ProposalList(
        requestId, request.budgetMin(), request.budgetMax(), items.size(), items);
  }

  public ProposalCard get(Long actorId, Long proposalId) {
    Proposal proposal =
        proposals.findById(proposalId).orElseThrow(ProposalReadService::notFound);
    RequestSnapshot request = ports.requests().load(proposal.getCustomRequestId());
    boolean owner = request.customerId().equals(actorId);
    boolean author = proposal.getChefId().equals(actorId);
    if (!owner && !author) {
      throw forbidden();
    }
    if (!author && proposal.getStatus() == ProposalStatus.DRAFT) {
      throw notFound();
    }
    return card(
        proposal,
        request,
        chefsOf(List.of(proposal)),
        OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE));
  }

  private Map<Long, ChefSummary> chefsOf(List<Proposal> list) {
    return chefs.load(list.stream().map(Proposal::getChefId).collect(Collectors.toSet()));
  }

  private static ProposalCard card(
      Proposal proposal,
      RequestSnapshot request,
      Map<Long, ChefSummary> summaries,
      OffsetDateTime now) {
    ChefSummary chef = summaries.get(proposal.getChefId());
    ProposalStatus status = proposal.statusAt(now);
    boolean live = status == ProposalStatus.SENT || status == ProposalStatus.REVISED;
    return new ProposalCard(
        proposal.getId(),
        proposal.getCustomRequestId(),
        chef,
        proposal.getProposedPrice(),
        budgetDifference(request, proposal.getProposedPrice()),
        proposal.getDishes(),
        proposal.getNote(),
        proposal.getExpiresAt(),
        status,
        proposal.getRevision(),
        proposal.getVersion(),
        live && chef != null && chef.active(),
        proposal.getAcceptedBookingId(),
        proposal.getAcceptedOrderCode());
  }

  private static BudgetDifference budgetDifference(RequestSnapshot request, BigDecimal price) {
    BigDecimal ceiling = request.budgetMax() != null ? request.budgetMax() : request.budgetMin();
    if (ceiling == null) {
      return null;
    }
    BigDecimal amount = price.subtract(ceiling);
    BigDecimal percent =
        ceiling.signum() == 0
            ? BigDecimal.ZERO
            : amount.multiply(HUNDRED).divide(ceiling, 1, RoundingMode.HALF_UP);
    boolean within = request.budgetMax() == null || price.compareTo(request.budgetMax()) <= 0;
    return new BudgetDifference(amount, percent, within);
  }

  private static Comparator<ProposalCard> order(ProposalSort sort) {
    Comparator<ProposalCard> acceptableFirst =
        Comparator.comparing(ProposalCard::acceptable).reversed();
    Comparator<ProposalCard> withinBudgetFirst =
        Comparator.comparing(
                (ProposalCard card) ->
                    card.budgetDifference() == null || card.budgetDifference().withinBudget())
            .reversed();
    Comparator<ProposalCard> ratingHigh =
        Comparator.comparing(
            (ProposalCard card) -> card.chef() == null ? BigDecimal.ZERO : card.chef().rating(),
            Comparator.reverseOrder());
    Comparator<ProposalCard> priceLow = Comparator.comparing(ProposalCard::proposedPrice);
    Comparator<ProposalCard> oldestFirst = Comparator.comparing(ProposalCard::id);

    return switch (sort) {
      case RECOMMENDED ->
          acceptableFirst
              .thenComparing(withinBudgetFirst)
              .thenComparing(ratingHigh)
              .thenComparing(priceLow)
              .thenComparing(oldestFirst);
      case PRICE -> acceptableFirst.thenComparing(priceLow).thenComparing(oldestFirst);
      case RATING ->
          acceptableFirst.thenComparing(ratingHigh).thenComparing(priceLow).thenComparing(oldestFirst);
    };
  }

  private static ProposalException forbidden() {
    return new ProposalException(
        HttpStatus.FORBIDDEN, ErrorCode.PROPOSAL_FORBIDDEN, "Bạn không có quyền xem báo giá này.");
  }

  private static ProposalException notFound() {
    return new ProposalException(
        HttpStatus.NOT_FOUND, ErrorCode.PROPOSAL_NOT_FOUND, "Không tìm thấy báo giá.");
  }
}
