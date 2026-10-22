package com.james.LMS.proposal;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

  List<Proposal> findByCustomRequestIdOrderById(Long customRequestId);

  /** Locks every proposal of a request, always in id order so concurrent accepts cannot deadlock. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Proposal p where p.customRequestId = :requestId order by p.id")
  List<Proposal> lockByCustomRequestId(@Param("requestId") Long requestId);

  boolean existsByCustomRequestIdAndChefIdAndStatusIn(
      Long customRequestId, Long chefId, Collection<ProposalStatus> statuses);

  List<Proposal> findByCustomRequestIdAndStatusInAndExpiresAtBefore(
      Long customRequestId, Collection<ProposalStatus> statuses, OffsetDateTime before);
}
