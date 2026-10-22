package com.james.LMS.payment.escrow;

import com.james.LMS.entity.PaymentTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Native access to the release function and the few facts around it. */
public interface EscrowRepository extends Repository<PaymentTransaction, Long> {

  /** Locks the order row and returns its customer id (0 when the order has none). */
  @Query(
      value = "select coalesce(o.customer_id, 0) from orders o where o.id = :orderId for update",
      nativeQuery = true)
  Optional<Long> lockOrderCustomer(@Param("orderId") Long orderId);

  @Query(
      value =
          "select exists(select 1 from order_escrows e where e.order_id = :orderId"
              + " and e.status = 'RELEASED')",
      nativeQuery = true)
  boolean isReleased(@Param("orderId") Long orderId);

  @Query(
      value =
          "select exists(select 1 from admin_cases c where c.booking_id = :orderId"
              + " and c.status in ('OPEN', 'IN_REVIEW'))",
      nativeQuery = true)
  boolean hasOpenDispute(@Param("orderId") Long orderId);

  @Query(
      value = "select gocook_accept_and_release(:orderId, :customerId)",
      nativeQuery = true)
  Boolean acceptAndRelease(
      @Param("orderId") Long orderId, @Param("customerId") Long customerId);
}
