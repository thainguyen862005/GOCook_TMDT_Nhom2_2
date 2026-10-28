package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.User;
import com.james.LMS.request.ChangePasswordRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountPasswordServiceTest {
  @AfterEach void teardown() { SecurityContextHolder.clearContext(); }
  @Test void wrongCurrentPasswordNeverRevokesOrChanges() {
    UserService users = mock(UserService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AuthSessionService sessions = mock(AuthSessionService.class);
    User user = User.builder().email("owner@example.com").password("old-hash").build();
    SecurityUserDetails actor = SecurityUserDetails.build(user, List.of());
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
    assertThrows(BadCredentialsException.class, () -> new AccountPasswordService(users, encoder, sessions)
        .change(new ChangePasswordRequest("wrong", "new-password-long-enough", "new-password-long-enough")));
    verify(users, never()).save(any());
    verifyNoInteractions(sessions);
  }
}
