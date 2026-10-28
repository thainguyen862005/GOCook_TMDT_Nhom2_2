package com.james.LMS.repository;

import com.james.LMS.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  // Booking writers must take the same lock before checking Chef availability.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id=:id and u.isActive=true")
  Optional<User> lockScheduleOwner(@Param("id") Long id);

  @Query("select u from User u where lower(trim(u.email)) = lower(trim(:email))")
  Optional<User> findByEmail(@Param("email") String email);

  @Query("select case when count(u) > 0 then true else false end from User u where lower(trim(u.email)) = lower(trim(:email))")
  Boolean existsUserByEmail(@Param("email") String email);

  @Query(value = "select u " + "from User u " + "where u.id = :id")
  Optional<User> findUserAndInstructorById(@Param("id") Long id);
}
