package com.james.LMS.facade.impl;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.Role;
import com.james.LMS.entity.User;
import com.james.LMS.repository.UserAuthIdentityRepository;
import com.james.LMS.enums.*;
import com.james.LMS.exception.*;
import com.james.LMS.facade.UserFacade;
import com.james.LMS.request.*;
import com.james.LMS.response.*;
import com.james.LMS.service.*;
import com.james.LMS.util.DateUtil;
import com.james.LMS.util.MailUtil;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFacadeImpl implements UserFacade {
  private static final int RESET_TOKEN_TTL_MINUTES = 15;

  private final UserService userService;
  private final JwtService jwtService;
  private final CacheService cacheService;
  private final PasswordEncoder passwordEncoder;
  private final RoleService roleService;
  private final MailProducerService mailProducerService;
  private final CloudinaryService cloudinaryService;
  private final AuthChallengeService challenges;
  private final AuthSessionService sessions;
  private final UserAuthIdentityRepository identities;

  @Override
  @Transactional
  public BaseResponse<Void> signUp(UpsertUserRequest upsertUserRequest) {
    String email = normalizeEmail(upsertUserRequest.getEmail());

    boolean isExistUser = this.userService.existsUserByEmail(email);
    // Do not reveal which addresses already belong to registered accounts.
    if (isExistUser) return BaseResponse.ok();

    String passwordEncoded = this.passwordEncoder.encode(upsertUserRequest.getPassword());
    User user =
        User.builder()
            .username(upsertUserRequest.getUsername().trim())
            .email(email)
            .password(passwordEncoded)
            .emailVerified(false)
            .build();

    Role userRole =
        this.roleService
            .findByRoleName(RoleEnum.USER)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ROLE_NOT_FOUND));
    user.addRole(userRole);

    // Persist PENDING verification. OTP is ephemeral in Redis (10 minutes); the user
    // stays pending indefinitely, and may request a new code after the cooldown.
    this.userService.save(user);
    if (challenges.reserveSend("verify", email)) {
      sendEmailVerificationCode(user);
    }
    return BaseResponse.ok();
  }

  @Override
  @Transactional
  public BaseResponse<Void> verifyEmail(VerifyEmailRequest verifyEmailRequest) {
    String email = normalizeEmail(verifyEmailRequest.getEmail());
    User user = userService.findByEmail(email).orElse(null);
    // Unknown, disabled and already-verified addresses share the same OTP response.
    if (user == null || !user.isActive() || user.isEmailVerified()) {
      throw new PermissionDeniedException(ErrorCode.NOT_MATCHED_OTP);
    }

    challenges.consume("verify", email, verifyEmailRequest.getOtp());

    user.verifyEmail();
    userService.save(user);


    return BaseResponse.ok();
  }

  @Override
  public BaseResponse<Void> resendVerification(
      ResendVerificationRequest resendVerificationRequest) {
    String email = normalizeEmail(resendVerificationRequest.getEmail());
    boolean permitted = challenges.reserveSend("verify", email);
    User user = userService.findByEmail(email).orElse(null);
    if (permitted && user != null && user.isActive() && !user.isEmailVerified()) {
      sendEmailVerificationCode(user);
    }
    return BaseResponse.ok();
  }

  @Override
  public BaseResponse<RefreshTokenResponse> refreshToken(RefreshTokenRequest ignored) {
    // Legacy body-token refresh is intentionally disabled. Use the HttpOnly cookie endpoint.
    throw new InvalidTokenException(ErrorCode.JWT_INVALID);
  }

  @Override
  public BaseResponse<Void> logout() {
    // The endpoint uses AuthSessionService.logout(request, response) to invalidate the
    // exact refresh cookie, including when the access token has expired.
    throw new IllegalStateException("Use cookie-based logout endpoint");
  }

  @Override
  @Transactional
  public BaseResponse<Void> resetPassword(ResetPasswordRequest resetPasswordRequest) {
    boolean isValidPassword =
        resetPasswordRequest.getNewPassword().equals(resetPasswordRequest.getConfirmPassword());
    if (!isValidPassword) throw new PermissionDeniedException(ErrorCode.NOT_MATCHED_PASSWORD);

    String resetPasswordToken = resetPasswordRequest.getResetPasswordToken();
    if (!jwtService.validateTokenForPurpose(resetPasswordToken, "reset")) {
      throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    }

    String email = normalizeEmail(jwtService.getEmailFromJwtToken(resetPasswordToken));
    String resetTokenKey = String.format(ResetPasswordKey.RESET_TOKEN_KEY.getContent(), email);

    User user =
        userService
            .findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    if (passwordEncoder.matches(resetPasswordRequest.getNewPassword(), user.getPassword())) {
      throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ.");
    }
    // One-use even if two requests arrive concurrently. Failed persistence needs a new reset code.
    sessions.consumeResetToken(resetTokenKey, resetPasswordToken);
    String newPasswordEncoded = passwordEncoder.encode(resetPasswordRequest.getNewPassword());
    user.changePassword(newPasswordEncoded);
    user.enablePasswordLogin();
    user.verifyEmail(); // The successful recovery OTP proved control of the mailbox.
    userService.save(user);

    challenges.invalidateChallenge("reset", email);
    sessions.revokeAll(email);

    return BaseResponse.ok();
  }

  @Override
  public BaseResponse<ForgotPasswordResponse> forgotPassword(
      ForgotPasswordRequest forgotPasswordRequest) {
    String email = normalizeEmail(forgotPasswordRequest.getEmail());
    boolean permitted = challenges.reserveSend("reset", email);
    User user = userService.findByEmail(email).orElse(null);
    // Enumeration-resistant response. Queue mail only for existing active accounts.
    if (permitted && user != null && user.isActive()) {
      cacheService.delete(String.format(ResetPasswordKey.RESET_TOKEN_KEY.getContent(), email));
      String otp = challenges.issue("reset", email);
      mailProducerService.send(MailUtil.buildMessageMailDTOForOTP(email, otp));
    }

    return BaseResponse.build(ForgotPasswordResponse.builder().build(), true);
  }

  @Override
  public BaseResponse<VerifyOTPResponse> verify(VerifyOTPRequest verifyOTPRequest) {
    String email = normalizeEmail(verifyOTPRequest.getEmail());
    User user = userService.findByEmail(email).orElse(null);
    if (user == null || !user.isActive()) {
      throw new PermissionDeniedException(ErrorCode.NOT_MATCHED_OTP);
    }

    challenges.consume("reset", email, verifyOTPRequest.getOtp());

    String resetPasswordToken = jwtService.generateResetPasswordToken(user.getEmail());
    String resetTokenKey =
        String.format(ResetPasswordKey.RESET_TOKEN_KEY.getContent(), user.getEmail());

    cacheService.store(
        resetTokenKey, resetPasswordToken, RESET_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
    return BaseResponse.build(
        VerifyOTPResponse.builder().resetPasswordToken(resetPasswordToken).build(), true);
  }

  @Override
  @Transactional(readOnly = true)
  public BaseResponse<UserDetailResponse> findProfile() {
    SecurityUserDetails principal =
        (SecurityUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    User user =
        userService
            .findByEmail(principal.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    UserDetailResponse userDetailResponse =
        UserDetailResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .emailVerified(user.isEmailVerified())
            .passwordLoginEnabled(user.isPasswordLoginEnabled())
            .googleLinked(identities.existsByProviderAndUserId("GOOGLE", user.getId()))
            .avatarUrl(user.getAvatarUrl())
            .phone(user.getPhone())
            .defaultAddress(user.getDefaultAddress())
            .createdAt(DateUtil.convertToLocalDate(user.getCreatedAt()))
            .roles(
                principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .toList())
            .build();
    return BaseResponse.build(userDetailResponse, true);
  }

  @Override
  @Transactional(readOnly = true)
  public BaseResponse<UserDetailResponse> findDetailById(Long id) {
    User user =
        userService
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    UserDetailResponse userDetailResponse =
        UserDetailResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .emailVerified(user.isEmailVerified())
            .passwordLoginEnabled(user.isPasswordLoginEnabled())
            .googleLinked(identities.existsByProviderAndUserId("GOOGLE", user.getId()))
            .avatarUrl(user.getAvatarUrl())
            .createdAt(DateUtil.convertToLocalDate(user.getCreatedAt()))
            .build();
    return BaseResponse.build(userDetailResponse, true);
  }

  @Override
  @Transactional
  public BaseResponse<String> uploadFile(byte[] bytes) {
    SecurityUserDetails principal =
        (SecurityUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    User user =
        userService
            .findByEmail(principal.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    String avatarUrl = this.cloudinaryService.uploadFile(bytes, FileType.IMAGE);
    user.addAvatarUrl(avatarUrl);
    return BaseResponse.build(avatarUrl, true);
  }

  @Override
  @Transactional
  public BaseResponse<Void> updateProfile(UpdateUserProfileRequest updateUserProfileRequest) {
    SecurityUserDetails principal =
        (SecurityUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    User user =
        userService
            .findByEmail(principal.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    String name = updateUserProfileRequest.getUsername();
    if (name == null || name.trim().length() < 2 || name.trim().length() > 80) {
      throw new IllegalArgumentException("Tên hiển thị cần từ 2 đến 80 ký tự.");
    }
    user.changeUsername(name.trim());
    if (updateUserProfileRequest.getPhone() != null) {
      user.changePhone(updateUserProfileRequest.getPhone().trim());
    }
    if (updateUserProfileRequest.getDefaultAddress() != null) {
      user.changeDefaultAddress(updateUserProfileRequest.getDefaultAddress().trim());
    }
    this.userService.save(user);
    return BaseResponse.ok();
  }

  private void sendEmailVerificationCode(User user) {
    String email = normalizeEmail(user.getEmail());
    String otp = challenges.issue("verify", email);
    mailProducerService.send(MailUtil.buildMessageMailDTOForEmailVerification(email, otp));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
