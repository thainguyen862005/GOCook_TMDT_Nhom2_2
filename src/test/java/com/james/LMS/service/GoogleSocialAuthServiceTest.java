package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.Role;
import com.james.LMS.entity.User;
import com.james.LMS.entity.UserAuthIdentity;
import com.james.LMS.enums.RoleEnum;
import com.james.LMS.repository.UserAuthIdentityRepository;
import com.james.LMS.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.james.LMS.config.SecurityUserDetails;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class GoogleSocialAuthServiceTest {
  @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
  private final GoogleIdentityVerifier verifier = mock(GoogleIdentityVerifier.class);
  private final AuthChallengeService challenges = mock(AuthChallengeService.class);
  private final UserAuthIdentityRepository identities = mock(UserAuthIdentityRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final RoleService roles = mock(RoleService.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final AuthSessionService sessions = mock(AuthSessionService.class);
  private final GoogleSocialAuthService service = new GoogleSocialAuthService(
      verifier, challenges, identities, users, roles, encoder, sessions);

  @Test void existingPasswordAccountCannotBeAutoLinkedByMatchingEmail() {
    var google = new GoogleIdentityVerifier.Identity("google-sub", "owner@gmail.com", "Owner", true, "nonce");
    when(users.existsUserByEmail("owner@gmail.com")).thenReturn(true);
    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.signIn(google, new MockHttpServletResponse()));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(identities, never()).save(any());
    verifyNoInteractions(sessions);
  }

  @Test void firstGoogleLoginCreatesPendingAccountWithoutPasswordLoginForNonHostedEmail() {
    var google = new GoogleIdentityVerifier.Identity("google-sub", "owner@example.net", "Owner", false, "nonce");
    when(roles.findByRoleName(RoleEnum.USER)).thenReturn(Optional.of(Role.builder().roleName(RoleEnum.USER).build()));
    when(encoder.encode(anyString())).thenReturn("$2a$12$random-hash");
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    service.signIn(google, new MockHttpServletResponse());
    var saved = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertFalse(saved.getValue().isPasswordLoginEnabled());
    assertFalse(saved.getValue().isEmailVerified());
    assertEquals("owner@example.net", saved.getValue().getEmail());
    verify(identities).save(any(UserAuthIdentity.class));
  }

  @Test void googleHostedNewAccountEmailIsVerified() {
    var google = new GoogleIdentityVerifier.Identity("google-sub", "owner@gmail.com", "Owner", true, "nonce");
    when(roles.findByRoleName(RoleEnum.USER)).thenReturn(Optional.of(Role.builder().roleName(RoleEnum.USER).build()));
    when(encoder.encode(anyString())).thenReturn("encoded");
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    service.signIn(google, new MockHttpServletResponse());
    var saved = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertTrue(saved.getValue().isEmailVerified());
    assertFalse(saved.getValue().isPasswordLoginEnabled());
  }

  @Test void repeatedGoogleLoginUsesProviderSubjectRatherThanEmail() {
    User existing = User.builder().email("owner@gmail.com").isActive(true).build();
    when(identities.findByProviderAndSubject("GOOGLE", "google-sub"))
        .thenReturn(Optional.of(new UserAuthIdentity("GOOGLE", "google-sub", existing)));
    service.signIn(new GoogleIdentityVerifier.Identity("google-sub", "owner@gmail.com", "Owner", true, "nonce"),
        new MockHttpServletResponse());
    verify(users, never()).save(any());
    verify(sessions).create(eq("owner@gmail.com"), any());
  }

  @Test void credentialIsRejectedWhenNonceHasBeenConsumed() {
    var google = new GoogleIdentityVerifier.Identity("subject", "owner@gmail.com", "Owner", true, "nonce");
    when(verifier.verify("token")).thenReturn(google);
    doThrow(new org.springframework.security.authentication.BadCredentialsException("replayed"))
        .when(challenges).consumeGoogleNonce("nonce");
    assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
        () -> service.verified("token"));
    verifyNoInteractions(sessions);
  }
  private void signedInAs(User user) {
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    var principal = SecurityUserDetails.build(user, List.of());
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @Test void linkRejectsGoogleAccountWithAnotherEmail() {
    User local = User.builder().id(23L).email("local@example.com").isActive(true).build();
    signedInAs(local);
    var another = new GoogleIdentityVerifier.Identity("subject-123", "other@gmail.com", "Other", true, "nonce");
    var ex = assertThrows(ResponseStatusException.class, () -> service.link(another));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(identities, never()).save(any());
  }

  @Test void explicitGoogleLinkRequiresAuthenticatedAccountAndCanVerifyGoogleHostedEmail() {
    User local = User.builder().id(22L).email("owner@gmail.com").isActive(true).emailVerified(false).build();
    signedInAs(local);
    service.link(new GoogleIdentityVerifier.Identity("subject", "owner@gmail.com", "Owner", true, "nonce"));
    verify(identities).save(any(UserAuthIdentity.class));
    assertTrue(local.isEmailVerified());
  }

  @Test void googleOnlyAccountCanSetManualPasswordOnce() {
    User googleOnly = User.builder().id(42L).email("owner@gmail.com").isActive(true)
        .password("unusable-hash").passwordLoginEnabled(false).build();
    signedInAs(googleOnly);
    when(identities.existsByProviderAndUserId("GOOGLE", 42L)).thenReturn(true);
    when(encoder.encode("very-long-new-password")).thenReturn("new-encoded-hash");
    service.setInitialPassword("very-long-new-password", "very-long-new-password");
    assertEquals("new-encoded-hash", googleOnly.getPassword());
    assertTrue(googleOnly.isPasswordLoginEnabled());
    verify(sessions).revokeAll("owner@gmail.com");
    assertThrows(ResponseStatusException.class, () ->
        service.setInitialPassword("very-long-new-password", "very-long-new-password"));
  }

}
