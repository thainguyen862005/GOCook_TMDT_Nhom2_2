package com.james.LMS.repository;

import com.james.LMS.entity.UserAuthIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {
  Optional<UserAuthIdentity> findByProviderAndSubject(String provider, String subject);
  boolean existsByProviderAndUserId(String provider, Long userId);
}
