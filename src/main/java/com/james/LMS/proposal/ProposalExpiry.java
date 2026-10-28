package com.james.LMS.proposal;

import com.james.LMS.service.PaymentGateway;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lazy expiry: every write command first saves EXPIRED for the overdue proposals of its own
 * request, in a transaction of its own so the change survives when the command itself fails.
 */
@Component
@RequiredArgsConstructor
public class ProposalExpiry {

  private static final List<ProposalStatus> EXPIRABLE =
      List.of(ProposalStatus.SENT, ProposalStatus.REVISED);

  private final ProposalRepository proposals;
  private final TransactionTemplate transactions;

  public void expireDue(Long requestId) {
    transactions.executeWithoutResult(
        status -> {
          List<Proposal> due =
              proposals.findByCustomRequestIdAndStatusInAndExpiresAtBefore(
                  requestId, EXPIRABLE, OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE));
          due.forEach(proposal -> proposal.setStatus(ProposalStatus.EXPIRED));
          proposals.saveAll(due);
        });
  }
}
