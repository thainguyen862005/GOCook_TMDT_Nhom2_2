package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.User;
import com.james.LMS.facade.impl.UserFacadeV2Impl;
import com.james.LMS.request.LoginRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthLoginFlowTest {
  @Test void wrongPasswordNeverIssuesSession() {
    UserService users = mock(UserService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AuthChallengeService challenges = mock(AuthChallengeService.class);
    AuthSessionService sessions = mock(AuthSessionService.class);
    User user = User.builder().email("user@example.com").password("hashed").build();
    when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(encoder.matches("incorrect", "hashed")).thenReturn(false);
    UserFacadeV2Impl facade = new UserFacadeV2Impl(users, encoder, challenges, sessions);
    assertThrows(BadCredentialsException.class, () ->
        facade.login(new LoginRequest("USER@EXAMPLE.COM", "incorrect"), new MockHttpServletResponse()));
    verify(challenges).failedLogin("user@example.com");
    verifyNoInteractions(sessions);
  }

  @Test void validPasswordIssuesSessionWithoutRequiringEmailVerification() {
    UserService users = mock(UserService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AuthChallengeService challenges = mock(AuthChallengeService.class);
    AuthSessionService sessions = mock(AuthSessionService.class);
    User pending = User.builder().email("pending@example.com").password("hashed")
        .emailVerified(false).build();
    when(users.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
    when(encoder.matches("long-valid-password", "hashed")).thenReturn(true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertTrue(new UserFacadeV2Impl(users, encoder, challenges, sessions)
        .login(new LoginRequest("pending@example.com", "long-valid-password"), response).isSuccess());
    verify(sessions).create("pending@example.com", response);
  }

  @Test void googleOnlyUserCannotLoginWithPasswordBeforeSettingOne() {
    UserService users = mock(UserService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AuthChallengeService challenges = mock(AuthChallengeService.class);
    AuthSessionService sessions = mock(AuthSessionService.class);
    User googleUser = User.builder().email("google@gmail.com").password("random-hash")
        .passwordLoginEnabled(false).isActive(true).build();
    when(users.findByEmail("google@gmail.com")).thenReturn(Optional.of(googleUser));
    when(encoder.matches(anyString(), anyString())).thenReturn(true);
    assertThrows(BadCredentialsException.class, () ->
        new UserFacadeV2Impl(users, encoder, challenges, sessions).login(
            new LoginRequest("google@gmail.com", "guessed-password"), new MockHttpServletResponse()));
    verifyNoInteractions(sessions);
  }

}
