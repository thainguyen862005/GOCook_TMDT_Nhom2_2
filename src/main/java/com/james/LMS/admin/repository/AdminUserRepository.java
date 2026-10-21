package com.james.LMS.admin.repository;

import com.james.LMS.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminUserRepository extends JpaRepository<User, Long> {

  @Query(
      value =
          """
          SELECT u.*
          FROM users u
          WHERE (:query = ''
                 OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:active IS NULL OR u.is_active = :active)
            AND (:roleName IS NULL OR EXISTS (
                SELECT 1
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = u.id
                  AND ur.is_active = true
                  AND r.role_name = :roleName
            ))
          ORDER BY u.created_at DESC
          """,
      countQuery =
          """
          SELECT COUNT(*)
          FROM users u
          WHERE (:query = ''
                 OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:active IS NULL OR u.is_active = :active)
            AND (:roleName IS NULL OR EXISTS (
                SELECT 1
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = u.id
                  AND ur.is_active = true
                  AND r.role_name = :roleName
            ))
          """,
      nativeQuery = true)
  Page<User> search(
      @Param("query") String query,
      @Param("roleName") String roleName,
      @Param("active") Boolean active,
      Pageable pageable);

  Optional<User> findByEmail(String email);

  // Serialize all administrative mutations which may affect the last active admin.
  @Query(value = "SELECT id FROM roles WHERE role_name = 'ADMIN' ORDER BY id LIMIT 1 FOR UPDATE", nativeQuery = true)
  Long lockAdminRole();

  @Query(
      value =
          """
          SELECT COUNT(DISTINCT u.id)
          FROM users u
          JOIN user_roles ur ON ur.user_id = u.id AND ur.is_active = true
          JOIN roles r ON r.id = ur.role_id
          WHERE u.is_active = true AND r.role_name = :roleName
          """,
      nativeQuery = true)
  long countActiveByRole(@Param("roleName") String roleName);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update User u set u.isActive = :active where u.id = :id")
  int updateActive(@Param("id") Long id, @Param("active") boolean active);
}
