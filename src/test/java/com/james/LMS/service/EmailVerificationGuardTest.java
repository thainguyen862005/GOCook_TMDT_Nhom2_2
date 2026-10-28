package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.User;
import com.james.LMS.exception.PermissionDeniedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class EmailVerificationGuardTest {
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void unverifiedCustomerCannotCheckout() {
    UserService users = mock(UserService.class);
    User pending = User.builder().email("pending@gocook.test").emailVerified(false).build();
    when(users.findByEmail("pending@gocook.test")).thenReturn(Optional.of(pending));
    SecurityUserDetails actor = SecurityUserDetails.build(pending, List.of());
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    assertThrows(PermissionDeniedException.class,
        () -> new EmailVerificationGuard(users).requireVerifiedCurrentUser());
  }

  @Test void verifiedCustomerMayCheckout() {
    UserService users = mock(UserService.class);
    User verified = User.builder().email("verified@gocook.test").emailVerified(true).build();
    when(users.findByEmail("verified@gocook.test")).thenReturn(Optional.of(verified));
    SecurityUserDetails actor = SecurityUserDetails.build(verified, List.of());
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    assertSame(verified, new EmailVerificationGuard(users).requireVerifiedCurrentUser());
  }
}
