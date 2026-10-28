package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.Role;
import com.james.LMS.entity.User;
import com.james.LMS.enums.RoleEnum;
import com.james.LMS.facade.impl.UserFacadeImpl;
import com.james.LMS.request.UpsertUserRequest;
import com.james.LMS.request.ResendVerificationRequest;
import com.james.LMS.repository.UserAuthIdentityRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationOtpStateTest {
  private final UserService users = mock(UserService.class);
  private final JwtService jwt = mock(JwtService.class);
  private final CacheService cache = mock(CacheService.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final RoleService roles = mock(RoleService.class);
  private final MailProducerService mail = mock(MailProducerService.class);
  private final CloudinaryService images = mock(CloudinaryService.class);
  private final AuthChallengeService challenges = mock(AuthChallengeService.class);
  private final AuthSessionService sessions = mock(AuthSessionService.class);
  private final UserAuthIdentityRepository identities = mock(UserAuthIdentityRepository.class);
  private final UserFacadeImpl facade = new UserFacadeImpl(users, jwt, cache, encoder, roles, mail,
      images, challenges, sessions, identities);

  @Test void newRegistrationCreatesPendingUserAndSendsOtp() {
    when(encoder.encode("long-enough-password")).thenReturn("hash");
    when(roles.findByRoleName(RoleEnum.USER)).thenReturn(Optional.of(Role.builder().roleName(RoleEnum.USER).build()));
    when(challenges.reserveSend("verify", "new@example.com")).thenReturn(true);
    when(challenges.issue("verify", "new@example.com")).thenReturn("123456");
    assertTrue(facade.signUp(new UpsertUserRequest(null, "New User", "NEW@example.com", "long-enough-password")).isSuccess());
    var captured = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).save(captured.capture());
    assertFalse(captured.getValue().isEmailVerified());
    assertTrue(captured.getValue().isPasswordLoginEnabled());
    verify(mail).send(any());
  }

  @Test void repeatedRegistrationNeverOverwritesExistingUserOrSendsOtp() {
    when(users.existsUserByEmail("known@example.com")).thenReturn(true);
    assertTrue(facade.signUp(new UpsertUserRequest(null, "Known", "known@example.com", "long-enough-password")).isSuccess());
    verify(users, never()).save(any());
    verifyNoInteractions(mail);
  }

  @Test void expiredOtpCanBeReissuedWithoutRecreatingAccount() {
    User pending = User.builder().email("pending@example.com").emailVerified(false).isActive(true).build();
    when(users.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
    when(challenges.reserveSend("verify", "pending@example.com")).thenReturn(true);
    when(challenges.issue("verify", "pending@example.com")).thenReturn("654321");
    assertTrue(facade.resendVerification(new ResendVerificationRequest("pending@example.com")).isSuccess());
    verify(mail).send(any());
    verify(users, never()).save(any());
  }
}
