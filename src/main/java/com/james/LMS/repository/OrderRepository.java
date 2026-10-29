
package com.james.LMS.repository;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface OrderRepository
        extends JpaRepository<CookOrder, Long>,
        JpaSpecificationExecutor<CookOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CookOrder o WHERE o.id = :id")
    Optional<CookOrder> findByIdForUpdate(
            @Param("id") Long id);

    List<CookOrder> findTop5ByOrderByCreatedAtDesc();

    Page<CookOrder> findByCustomerIdAndIsActiveTrue(
            Long customerId,
            Pageable pageable);

    Page<CookOrder> findByCustomerIdAndStatusAndIsActiveTrue(
            Long customerId,
            OrderStatus status,
            Pageable pageable);

    Optional<CookOrder> findByIdAndCustomerIdAndIsActiveTrue(
            Long id,
            Long customerId);

    Optional<CookOrder> findByOrderCode(String orderCode);

    @Query("select coalesce(sum(o.totalAmount), 0) " +
            "from CookOrder o where o.status = :status")
    BigDecimal sumByStatus(
            @Param("status") OrderStatus status);

    @Query("""
            select coalesce(sum(o.totalAmount), 0)
            from CookOrder o
            where o.status = :status
              and o.scheduledAt >= :from
              and o.scheduledAt < :to
            """)
    BigDecimal sumByStatusAndScheduledAtBetween(
            @Param("status") OrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
