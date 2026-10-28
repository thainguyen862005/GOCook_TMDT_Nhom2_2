package com.james.LMS.repository;

import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.enums.PaymentProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByOrderIdInAndIsActiveTrueOrderByCreatedAtDescIdDesc(
            Collection<Long> orderIds);

    Optional<PaymentTransaction> findFirstByOrderIdAndIsActiveTrueOrderByCreatedAtDescIdDesc(
            Long orderId);

    Optional<PaymentTransaction> findByProviderAndTransactionCode(
            PaymentProvider provider, String transactionCode);

    @Query("select p from PaymentTransaction p"
            + " where p.provider = :provider and p.transactionCode = :transactionCode")
    Optional<PaymentTransaction> lockByProviderAndTransactionCode(
            @Param("provider") PaymentProvider provider,
            @Param("transactionCode") String transactionCode);

    @Query(value = "select gocook_register_verified_payment(:orderId, :paymentId)", nativeQuery = true)
    Boolean registerVerifiedPayment(
            @Param("orderId") Long orderId, @Param("paymentId") Long paymentId);
}
