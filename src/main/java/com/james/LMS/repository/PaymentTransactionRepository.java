package com.james.LMS.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.enums.PaymentProvider;

import jakarta.persistence.LockModeType;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByOrderIdInAndIsActiveTrueOrderByCreatedAtDescIdDesc(
            Collection<Long> orderIds);

    Optional<PaymentTransaction> findFirstByOrderIdAndIsActiveTrueOrderByCreatedAtDescIdDesc(
            Long orderId);
            /**
 * Lấy giao dịch thanh toán chính của đơn hàng từ escrow.
 * Tránh lấy nhầm giao dịch thử lại hoặc giao dịch trùng.
 */
@Query(value = """
        SELECT p.*
        FROM payment_transactions p
        JOIN order_escrows e
            ON e.payment_transaction_id = p.id
        WHERE e.order_id IN (:orderIds)
          AND p.is_active = true
        """, nativeQuery = true)
List<PaymentTransaction> findEscrowPaymentsForOrders(
        @Param("orderIds") Collection<Long> orderIds
);

    Optional<PaymentTransaction> findByProviderAndTransactionCode(
            PaymentProvider provider, String transactionCode);

  Optional<PaymentTransaction> findByIdempotencyKey(UUID idempotencyKey);

  List<PaymentTransaction> findByOrderIdOrderByAttemptNoAsc(Long orderId);

  /** PENDING attempts whose expiry is before the cutoff, oldest first. */
  @Query(
      "select p from PaymentTransaction p where p.status = com.james.LMS.enums.PaymentStatus.PENDING"
          + " and p.expiresAt < :cutoff order by p.expiresAt")
  List<PaymentTransaction> findDuePending(
      @Param("cutoff") OffsetDateTime cutoff, Pageable page);

  /** payment_transaction_id of the order's escrow, if the order has one. */
  @Query(
      value = "select e.payment_transaction_id from order_escrows e where e.order_id = :orderId",
      nativeQuery = true)
  Optional<Long> findEscrowedPaymentId(@Param("orderId") Long orderId);

  /**
   * SUCCESS payments that are not the one held in escrow: duplicate payments to refund, or a
   * payment still waiting to be settled.
   */
  @Query(
      value =
          """
          select p.* from payment_transactions p
          where p.status = 'SUCCESS'
            and not exists (select 1 from order_escrows e where e.payment_transaction_id = p.id)
          order by p.id
          """,
      nativeQuery = true)
  List<PaymentTransaction> findSuccessWithoutEscrow();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from PaymentTransaction p"
          + " where p.provider = :provider and p.transactionCode = :transactionCode")
  Optional<PaymentTransaction> lockByProviderAndTransactionCode(
      @Param("provider") PaymentProvider provider,
      @Param("transactionCode") String transactionCode);

    @Query(value = "select gocook_register_verified_payment(:orderId, :paymentId)", nativeQuery = true)
    Boolean registerVerifiedPayment(
            @Param("orderId") Long orderId, @Param("paymentId") Long paymentId);
}
