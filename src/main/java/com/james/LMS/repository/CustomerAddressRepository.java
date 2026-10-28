package com.james.LMS.repository;

import com.james.LMS.entity.CustomerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
  List<CustomerAddress> findByUserIdAndIsActiveTrueOrderByDefaultAddressDescIdAsc(Long userId);
  Optional<CustomerAddress> findByIdAndUserIdAndIsActiveTrue(Long id, Long userId);
}
