package com.james.LMS.proposal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.james.LMS.proposal.ChefSummaryReader.ChefSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** In-memory stand-in for ProposalRepository plus the other collaborators the services share. */
final class ProposalStore {
  final Map<Long, Proposal> byId = new LinkedHashMap<>();
  final AtomicLong ids = new AtomicLong(100);
  final ProposalRepository repository = mock(ProposalRepository.class);
  final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
  final TransactionTemplate transactions = new TransactionTemplate(txManager);
  final ChefSummaryReader chefs = mock(ChefSummaryReader.class);
  final Map<Long, ChefSummary> chefDirectory = new LinkedHashMap<>();

  ProposalStore() {
    when(repository.saveAndFlush(any(Proposal.class)))
        .thenAnswer(call -> persist(call.getArgument(0)));
    when(repository.saveAll(any()))
        .thenAnswer(call -> persistAll(call.getArgument(0)));
    when(repository.saveAllAndFlush(any()))
        .thenAnswer(call -> persistAll(call.getArgument(0)));
    when(repository.findById(anyLong()))
        .thenAnswer(call -> Optional.ofNullable(byId.get((Long) call.getArgument(0))));
    when(repository.findByCustomRequestIdOrderById(anyLong()))
        .thenAnswer(call -> ofRequest(call.getArgument(0)));
    when(repository.lockByCustomRequestId(anyLong()))
        .thenAnswer(call -> ofRequest(call.getArgument(0)));
    when(repository.existsByCustomRequestIdAndChefIdAndStatusIn(anyLong(), anyLong(), any()))
        .thenAnswer(
            call -> {
              Collection<ProposalStatus> statuses = call.getArgument(2);
              return ofRequest(call.getArgument(0)).stream()
                  .anyMatch(
                      p ->
                          p.getChefId().equals(call.getArgument(1))
                              && statuses.contains(p.getStatus()));
            });
    when(repository.findByCustomRequestIdAndStatusInAndExpiresAtBefore(anyLong(), any(), any()))
        .thenAnswer(
            call -> {
              Collection<ProposalStatus> statuses = call.getArgument(1);
              OffsetDateTime before = call.getArgument(2);
              return ofRequest(call.getArgument(0)).stream()
                  .filter(p -> statuses.contains(p.getStatus()) && p.getExpiresAt().isBefore(before))
                  .toList();
            });
    when(chefs.load(any()))
        .thenAnswer(
            call -> {
              Collection<Long> wanted = call.getArgument(0);
              Map<Long, ChefSummary> found = new LinkedHashMap<>();
              wanted.forEach(
                  id -> {
                    if (chefDirectory.containsKey(id)) {
                      found.put(id, chefDirectory.get(id));
                    }
                  });
              return found;
            });
  }

  ProposalStore chef(long id, String name, String rating, boolean active) {
    chefDirectory.put(id, new ChefSummary(id, name, new BigDecimal(rating), true, active));
    return this;
  }

  Proposal add(
      long requestId, long chefId, String price, ProposalStatus status, OffsetDateTime expiresAt) {
    Proposal proposal =
        Proposal.builder()
            .customRequestId(requestId)
            .chefId(chefId)
            .proposedPrice(new BigDecimal(price))
            .dishes("Goi cuon; Ca kho to")
            .note("note")
            .expiresAt(expiresAt)
            .status(status)
            .build();
    return persist(proposal);
  }

  Proposal persist(Proposal proposal) {
    if (proposal.getId() == null) {
      ReflectionTestUtils.setField(proposal, "id", ids.incrementAndGet());
    } else if (byId.containsKey(proposal.getId())) {
      ReflectionTestUtils.setField(proposal, "version", proposal.getVersion() + 1);
    }
    byId.put(proposal.getId(), proposal);
    return proposal;
  }

  private List<Proposal> persistAll(Iterable<Proposal> proposals) {
    List<Proposal> saved = new ArrayList<>();
    proposals.forEach(proposal -> saved.add(persist(proposal)));
    return saved;
  }

  private List<Proposal> ofRequest(Long requestId) {
    return byId.values().stream()
        .filter(p -> p.getCustomRequestId().equals(requestId))
        .sorted(Comparator.comparing(Proposal::getId))
        .toList();
  }

  ProposalStatus statusOf(Proposal proposal) {
    return byId.get(proposal.getId()).getStatus();
  }
}
