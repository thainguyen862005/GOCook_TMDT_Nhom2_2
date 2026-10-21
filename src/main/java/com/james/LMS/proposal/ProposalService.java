package com.james.LMS.proposal;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.pricing.QuoteGuidanceService;
import com.james.LMS.proposal.PatchProposalRequest.Action;
import com.james.LMS.proposal.port.ProposalPorts;
import com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Chef side: create, edit, send and withdraw proposals. */
@Service
@RequiredArgsConstructor
public class ProposalService {

  private static final List<ProposalStatus> LIVE =
      List.of(ProposalStatus.DRAFT, ProposalStatus.SENT, ProposalStatus.REVISED);

  private final ProposalRepository proposals;
  private final ProposalPorts ports;
  private final ProposalExpiry expiry;
  private final QuoteGuidanceService guidance;
  private final ProposalProperties properties;
  private final TransactionTemplate transactions;

  public ProposalView create(Long chefId, Long requestId, CreateProposalRequest body) {
    expiry.expireDue(requestId);
    return transactions.execute(
        status -> {
          RequestSnapshot request = ports.requests().load(requestId);
          requireMatched(chefId, request);
          requireAccepting(request);
          requirePrice(body.proposedPrice());
          requireFutureExpiry(body.expiresAt());
          if (proposals.existsByCustomRequestIdAndChefIdAndStatusIn(requestId, chefId, LIVE)) {
            throw new ProposalException(
                HttpStatus.CONFLICT,
                ErrorCode.PROPOSAL_ALREADY_EXISTS,
                "Bạn đã có một báo giá đang hoạt động cho yêu cầu này.");
          }
          boolean send = Boolean.TRUE.equals(body.send());
          Proposal saved =
              proposals.saveAndFlush(
                  Proposal.builder()
                      .customRequestId(requestId)
                      .chefId(chefId)
                      .proposedPrice(body.proposedPrice())
                      .dishes(body.dishes())
                      .note(body.note())
                      .expiresAt(body.expiresAt())
                      .status(send ? ProposalStatus.SENT : ProposalStatus.DRAFT)
                      .build());
          return view(saved, request);
        });
  }

  public ProposalView update(
      Long chefId, Long requestId, Long proposalId, PatchProposalRequest body) {
    expiry.expireDue(requestId);
    return transactions.execute(
        status -> {
          RequestSnapshot request = ports.requests().load(requestId);
          Proposal proposal = ownedProposal(chefId, requestId, proposalId);
          requireOpen(proposal);
          if (!proposal.getVersion().equals(body.version())) {
            throw versionConflict();
          }

          if (body.action() == Action.WITHDRAW) {
            if (body.changesContent()) {
              throw invalid("Không thể vừa sửa nội dung vừa rút báo giá.");
            }
            transition(proposal, ProposalStatus.WITHDRAWN);
            return view(proposals.saveAndFlush(proposal), request);
          }
          if (!body.changesContent() && body.action() == null) {
            throw invalid("Không có thay đổi nào để lưu.");
          }

          requireMatched(chefId, request);
          requireAccepting(request);
          if (body.changesContent()) {
            applyContent(proposal, body);
          }
          if (body.action() == Action.SEND) {
            requireFutureExpiry(proposal.getExpiresAt());
            transition(proposal, ProposalStatus.SENT);
          }
          return view(proposals.saveAndFlush(proposal), request);
        });
  }

  private void applyContent(Proposal proposal, PatchProposalRequest body) {
    if (body.proposedPrice() != null) {
      requirePrice(body.proposedPrice());
      proposal.setProposedPrice(body.proposedPrice());
    }
    if (body.expiresAt() != null) {
      requireFutureExpiry(body.expiresAt());
      proposal.setExpiresAt(body.expiresAt());
    }
    if (body.dishes() != null) {
      proposal.setDishes(body.dishes());
    }
    if (body.note() != null) {
      proposal.setNote(body.note());
    }
    proposal.setRevision(proposal.getRevision() + 1);
    if (proposal.getStatus() == ProposalStatus.SENT) {
      transition(proposal, ProposalStatus.REVISED);
    }
  }

  private Proposal ownedProposal(Long chefId, Long requestId, Long proposalId) {
    Proposal proposal =
        proposals
            .findById(proposalId)
            .filter(found -> found.getCustomRequestId().equals(requestId))
            .orElseThrow(
                () ->
                    new ProposalException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.PROPOSAL_NOT_FOUND,
                        "Không tìm thấy báo giá."));
    if (!proposal.getChefId().equals(chefId)) {
      throw new ProposalException(
          HttpStatus.FORBIDDEN,
          ErrorCode.PROPOSAL_FORBIDDEN,
          "Bạn không có quyền với báo giá này.");
    }
    return proposal;
  }

  private static void requireOpen(Proposal proposal) {
    if (proposal.getStatus() == ProposalStatus.EXPIRED) {
      throw new ProposalException(
          HttpStatus.CONFLICT, ErrorCode.PROPOSAL_EXPIRED, "Báo giá đã hết hạn.");
    }
    boolean closed =
        proposal.getStatus() == ProposalStatus.WITHDRAWN
            || proposal.getStatus() == ProposalStatus.ACCEPTED
            || proposal.getStatus() == ProposalStatus.REJECTED;
    if (closed) {
      throw transitionNotAllowed(proposal.getStatus());
    }
  }

  private static void transition(Proposal proposal, ProposalStatus target) {
    if (!proposal.getStatus().canTransitionTo(target)) {
      throw transitionNotAllowed(proposal.getStatus());
    }
    proposal.setStatus(target);
  }

  private static ProposalException transitionNotAllowed(ProposalStatus from) {
    return new ProposalException(
        HttpStatus.CONFLICT,
        ErrorCode.PROPOSAL_INVALID_TRANSITION,
        "Báo giá đang ở trạng thái " + from + ", không thể thực hiện thao tác này.");
  }

  private static ProposalException versionConflict() {
    return new ProposalException(
        HttpStatus.CONFLICT,
        ErrorCode.PROPOSAL_VERSION_CONFLICT,
        "Báo giá vừa được thay đổi. Hãy tải lại rồi thử lại.");
  }

  private static ProposalException invalid(String message) {
    return new ProposalException(HttpStatus.BAD_REQUEST, ErrorCode.PROPOSAL_VALIDATION, message);
  }

  private static void requireMatched(Long chefId, RequestSnapshot request) {
    if (!request.matchedChefIds().contains(chefId)) {
      throw new ProposalException(
          HttpStatus.FORBIDDEN,
          ErrorCode.PROPOSAL_CHEF_NOT_MATCHED,
          "Bạn không nằm trong danh sách đầu bếp được ghép cho yêu cầu này.");
    }
  }

  private static void requireAccepting(RequestSnapshot request) {
    if (!request.acceptingProposals()) {
      throw new ProposalException(
          HttpStatus.CONFLICT,
          ErrorCode.PROPOSAL_REQUEST_CLOSED,
          "Yêu cầu này không còn nhận báo giá.");
    }
  }

  private static void requirePrice(BigDecimal price) {
    if (price == null || price.signum() <= 0) {
      throw new ProposalException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.PROPOSAL_INVALID_PRICE,
          "Giá báo phải lớn hơn 0.");
    }
  }

  private void requireFutureExpiry(OffsetDateTime expiresAt) {
    OffsetDateTime now = OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE);
    if (expiresAt == null
        || !expiresAt.isAfter(now)
        || expiresAt.isAfter(now.plus(properties.getMaxExpiry()))) {
      throw new ProposalException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.PROPOSAL_INVALID_EXPIRY,
          "Hạn báo giá phải ở tương lai và không quá %d ngày kể từ bây giờ."
              .formatted(properties.getMaxExpiry().toDays()));
    }
  }

  private ProposalView view(Proposal proposal, RequestSnapshot request) {
    return new ProposalView(
        proposal.getId(),
        proposal.getCustomRequestId(),
        proposal.getChefId(),
        proposal.getProposedPrice(),
        proposal.getDishes(),
        proposal.getNote(),
        proposal.getExpiresAt(),
        proposal.getRevision(),
        proposal.getVersion(),
        proposal.getStatus(),
        guidance.guide(proposal.getChefId(), request).verdictFor(proposal.getProposedPrice()));
  }
}
