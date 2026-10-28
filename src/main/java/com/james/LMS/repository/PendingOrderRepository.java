package com.james.LMS.repository;

import com.james.LMS.entity.CookOrder;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only view of unpaid orders for the expiry sweeper. */
public interface PendingOrderRepository extends Repository<CookOrder, Long> {

  /**
   * Ids of PENDING orders created before the given epoch millis that entered payment (have at least
   * one attempt) and have no SUCCESS attempt, oldest first. Paid orders stay PENDING until the chef
   * answers and orders that never entered payment are not ours to expire, so neither may fill the
   * page.
   */
  @Query(
      "select o.id from CookOrder o where o.status = com.james.LMS.enums.OrderStatus.PENDING"
          + " and o.createdAt < :before"
          + " and exists (select 1 from PaymentTransaction p where p.orderId = o.id)"
          + " and not exists (select 1 from PaymentTransaction s where s.orderId = o.id"
          + " and s.status = com.james.LMS.enums.PaymentStatus.SUCCESS)"
          + " order by o.createdAt")
  List<Long> findPendingCreatedBefore(@Param("before") long before, Pageable page);
}
