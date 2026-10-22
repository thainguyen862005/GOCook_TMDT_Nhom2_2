package com.james.LMS.proposal;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.proposal.ChefSummaryReader.ChefSummary;
import com.james.LMS.proposal.port.BookingPort.BookingCommand;
import com.james.LMS.proposal.port.BookingPort.BookingRef;
import com.james.LMS.proposal.port.BookingUnavailableException;
import com.james.LMS.proposal.port.ProposalPorts;
import com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot;
import com.james.LMS.service.PaymentGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accepts one proposal atomically: lock all proposals of the request, create the booking through
 * {@link com.james.LMS.proposal.port.BookingPort}, mark this one ACCEPTED and the other live ones
 * REJECTED. Any failure rolls everything back, including what the booking port wrote.
 */
@Service
@RequiredArgsConstructor
public class ProposalAcceptService {

  private final ProposalRepository proposals;
  private final ProposalPorts ports;
  private final ProposalExpiry expiry;
  private final ChefSummaryReader chefs;
  private final TransactionTemplate transactions;

  public AcceptResult accept(Long customerId, Long proposalId) {
    Long requestId =
        proposals
            .findById(proposalId)
            .orElseThrow(
                () ->
                    new ProposalException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.PROPOSAL_NOT_FOUND,
                        "Không tìm thấy báo giá."))
            .getCustomRequestId();
    expiry.expireDue(requestId);
    return transactions.execute(status -> acceptLocked(customerId, proposalId, requestId));
  }

  private AcceptResult acceptLocked(Long customerId, Long proposalId, Long requestId) {
    RequestSnapshot request = ports.requests().load(requestId);
    if (!request.customerId().equals(customerId)) {
      throw new ProposalException(
          HttpStatus.FORBIDDEN,
          ErrorCode.PROPOSAL_FORBIDDEN,
          "Chỉ chủ của yêu cầu mới được chấp nhận báo giá.");
    }

    List<Proposal> all = proposals.lockByCustomRequestId(requestId);
    Proposal target =
        all.stream()
            .filter(proposal -> proposal.getId().equals(proposalId))
            .findFirst()
            .orElseThrow(
                () ->
                    new ProposalException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.PROPOSAL_NOT_FOUND,
                        "Không tìm thấy báo giá."));

    if (target.getStatus() == ProposalStatus.ACCEPTED) {
      return result(target);
    }
    if (all.stream().anyMatch(proposal -> proposal.getStatus() == ProposalStatus.ACCEPTED)) {
      throw new ProposalException(
          HttpStatus.CONFLICT,
          ErrorCode.PROPOSAL_ALREADY_RESOLVED,
          "Yêu cầu này đã chấp nhận một báo giá khác.");
    }
    if (target.statusAt(OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE)) == ProposalStatus.EXPIRED) {
      throw new ProposalException(
          HttpStatus.CONFLICT, ErrorCode.PROPOSAL_EXPIRED, "Báo giá đã hết hạn.");
    }
    if (!target.getStatus().canTransitionTo(ProposalStatus.ACCEPTED)) {
      throw new ProposalException(
          HttpStatus.CONFLICT,
          ErrorCode.PROPOSAL_INVALID_TRANSITION,
          "Báo giá đang ở trạng thái " + target.getStatus() + ", không thể chấp nhận.");
    }
    ChefSummary chef = chefs.load(Set.of(target.getChefId())).get(target.getChefId());
    if (chef == null || !chef.active()) {
      throw new ProposalException(
          HttpStatus.CONFLICT,
          ErrorCode.PROPOSAL_CHEF_INACTIVE,
          "Đầu bếp này hiện không còn hoạt động.");
    }

    BookingRef booking = book(target, request);

    target.setStatus(ProposalStatus.ACCEPTED);
    target.setAcceptedBookingId(booking.bookingId());
    target.setAcceptedOrderCode(booking.orderCode());
    all.stream()
        .filter(proposal -> proposal != target)
        .filter(
            proposal ->
                proposal.getStatus() == ProposalStatus.SENT
                    || proposal.getStatus() == ProposalStatus.REVISED)
        .forEach(proposal -> proposal.setStatus(ProposalStatus.REJECTED));
    proposals.saveAllAndFlush(all);
    return result(target);
  }

  private BookingRef book(Proposal proposal, RequestSnapshot request) {
    try {
      return ports
          .bookings()
          .createFromProposal(
              new BookingCommand(
                  proposal.getId(),
                  proposal.getCustomRequestId(),
                  request.customerId(),
                  proposal.getChefId(),
                  proposal.getProposedPrice(),
                  proposal.getDishes(),
                  request.guestCount(),
                  request.scheduledAt()));
    } catch (BookingUnavailableException e) {
      throw new ProposalException(
          HttpStatus.CONFLICT,
          ErrorCode.PROPOSAL_BOOKING_UNAVAILABLE,
          "Khung giờ này không còn trống. Vui lòng chọn báo giá hoặc thời gian khác.");
    }
  }

  private static AcceptResult result(Proposal proposal) {
    return new AcceptResult(
        proposal.getId(),
        proposal.getAcceptedBookingId(),
        proposal.getAcceptedOrderCode(),
        proposal.getProposedPrice());
  }
}
