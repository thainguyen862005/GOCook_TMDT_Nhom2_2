package com.james.LMS.service;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.User;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.EntityNotFoundException;
import com.james.LMS.exception.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Server-side step-up verification guard for transactional operations.
 *
 * <p>Use this at the beginning of create-booking / create-order / checkout services. Browsing,
 * login, account profile and other read-only flows must not call this guard.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationGuard {

  private final UserService userService;

  public User requireVerifiedCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof SecurityUserDetails principal)) {
      throw new PermissionDeniedException(ErrorCode.PERMISSION_DENIES);
    }

    User user =
        userService
            .findByEmail(principal.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    if (!user.isActive() || !user.isEmailVerified()) {
      throw new PermissionDeniedException(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    return user;
  }
}
