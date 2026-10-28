package com.james.LMS.service;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.User;
import com.james.LMS.entity.UserAuthIdentity;
import com.james.LMS.enums.RoleEnum;
import com.james.LMS.repository.UserAuthIdentityRepository;
import com.james.LMS.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GoogleSocialAuthService {
  private static final String PROVIDER = "GOOGLE";
  private final GoogleIdentityVerifier verifier;
  private final AuthChallengeService challenges;
  private final UserAuthIdentityRepository identities;
  private final UserRepository users;
  private final RoleService roles;
  private final PasswordEncoder passwordEncoder;
  private final AuthSessionService sessions;
  private static final SecureRandom RANDOM = new SecureRandom();

  public GoogleIdentityVerifier.Identity verified(String idToken) {
    var identity = verifier.verify(idToken);
    challenges.consumeGoogleNonce(identity.nonce());
    return identity;
  }

  @Transactional
  public void signIn(GoogleIdentityVerifier.Identity identity, HttpServletResponse response) {
    User user = identities.findByProviderAndSubject(PROVIDER, identity.subject())
        .map(UserAuthIdentity::getUser).orElse(null);
    if (user == null) {
      // Do NOT silently merge Google with an existing password account simply by email.
      // The owner must sign in locally and explicitly link the Google identity.
      if (users.existsUserByEmail(identity.email())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Email đã có tài khoản. Hãy đăng nhập bằng mật khẩu rồi liên kết Google trong Hồ sơ.");
      }
      byte[] secret = new byte[48];
      RANDOM.nextBytes(secret);
      String unusablePassword = passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
      String proposedName = identity.displayName() == null ? "Khách hàng GO Cook" : identity.displayName().trim();
      if (proposedName.isBlank()) proposedName = "Khách hàng GO Cook";
      User candidate = User.builder().username(proposedName.substring(0, Math.min(proposedName.length(), 80)))
          .email(identity.email()).password(unusablePassword).passwordLoginEnabled(false)
          .emailVerified(identity.authoritativeEmail()).build();
      candidate.addRole(roles.findByRoleName(RoleEnum.USER)
          .orElseThrow(() -> new IllegalStateException("Missing ROLE_USER")));
      user = users.save(candidate);
      identities.save(new UserAuthIdentity(PROVIDER, identity.subject(), user));
    }
    if (!user.isActive()) throw new BadCredentialsException("Account unavailable");
    // New Google accounts with non-Google-hosted email remain pending verification.
    sessions.create(user.getEmail(), response);
  }

  @Transactional
  public void link(GoogleIdentityVerifier.Identity identity) {
    User user = currentUser();
    if (!user.isActive()) throw new BadCredentialsException("Account unavailable");
    if (!user.getEmail().equalsIgnoreCase(identity.email())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Email Google phải trùng email tài khoản GO Cook.");
    }
    var existing = identities.findByProviderAndSubject(PROVIDER, identity.subject());
    if (existing.isPresent()) {
      if (existing.get().getUser().getId().equals(user.getId())) return; // idempotent
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Google account already linked");
    }
    if (identities.existsByProviderAndUserId(PROVIDER, user.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản đã liên kết Google khác");
    }
    identities.save(new UserAuthIdentity(PROVIDER, identity.subject(), user));
    if (identity.authoritativeEmail() && !user.isEmailVerified()) user.verifyEmail();
  }

  @Transactional
  public void setInitialPassword(String newPassword, String confirmation) {
    if (newPassword == null || !newPassword.equals(confirmation)
        || newPassword.length() < 12 || newPassword.length() > 128) {
      throw new IllegalArgumentException("Mật khẩu mới không hợp lệ.");
    }
    User user = currentUser();
    if (user.isPasswordLoginEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Tài khoản đã có mật khẩu. Hãy dùng chức năng Đổi mật khẩu.");
    }
    if (!identities.existsByProviderAndUserId(PROVIDER, user.getId())) {
      throw new BadCredentialsException("Google account not linked");
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    user.enablePasswordLogin();
    // Existing Google session is revoked; sign in with the newly configured password.
    sessions.revokeAll(user.getEmail());
  }

  private User currentUser() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof SecurityUserDetails actor))
      throw new BadCredentialsException("Authentication required");
    return users.findById(actor.getId()).orElseThrow(() -> new BadCredentialsException("Authentication required"));
  }
}
