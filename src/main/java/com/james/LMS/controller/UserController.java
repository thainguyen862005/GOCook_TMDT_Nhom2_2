package com.james.LMS.controller;

import com.james.LMS.config.SecurityConfig;
import com.james.LMS.media.ImageFileValidator;
import org.springframework.web.server.ResponseStatusException;
import com.james.LMS.facade.UserFacade;
import com.james.LMS.service.AuthSessionService;
import com.james.LMS.service.AccountPasswordService;
import com.james.LMS.service.AuthChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import com.james.LMS.request.*;
import com.james.LMS.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserFacade userFacade;
  private final AuthSessionService sessions;
  private final AccountPasswordService passwords;
  private final AuthChallengeService challenges;

  private static final String REMEMBER_ME_COOKIE = "remember_me";

  @Value("${security.cookie.secure:false}")
  private boolean cookieSecure;

  @Value("${security.cookie.same-site:Lax}")
  private String cookieSameSite;

  @PostMapping("/sign-up")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Sign up to system by email, user name and password",
      tags = {"User APIs"})
  public BaseResponse<Void> signUp(@Valid @RequestBody UpsertUserRequest upsertUserRequest, HttpServletRequest request) {
    challenges.requireIpRate("signup", request.getRemoteAddr(), 50, 60);
    return this.userFacade.signUp(upsertUserRequest);
  }


  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<Void> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest verifyEmailRequest, HttpServletRequest request) {
    challenges.requireIpRate("verify", request.getRemoteAddr(), 150, 15);
    return this.userFacade.verifyEmail(verifyEmailRequest);
  }

  @PostMapping({"/resend-verification", "/request-email-verification"})
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<Void> resendVerification(
      @Valid @RequestBody ResendVerificationRequest resendVerificationRequest, HttpServletRequest request) {
    challenges.requireIpRate("resend", request.getRemoteAddr(), 100, 15);
    return this.userFacade.resendVerification(resendVerificationRequest);
  }

  @PostMapping("/refresh-token")
  @ResponseStatus(HttpStatus.OK)
  public BaseResponse<Void> refreshToken(HttpServletRequest request, HttpServletResponse response) {
    sessions.refresh(request, response);
    return BaseResponse.ok();
  }

  /** Idempotent even after access-token expiry; refresh cookie identifies the session. */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.OK)
  public BaseResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    sessions.logout(request, response);
    return BaseResponse.ok();
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<Void> resetPassword(
      @Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
    return this.userFacade.resetPassword(resetPasswordRequest);
  }

  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<ForgotPasswordResponse> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest, HttpServletRequest request) {
    challenges.requireIpRate("forgot", request.getRemoteAddr(), 60, 15);
    return this.userFacade.forgotPassword(forgotPasswordRequest);
  }

  @PostMapping("/verify-otp")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<VerifyOTPResponse> verify(
      @Valid @RequestBody VerifyOTPRequest verifyOTPRequest, HttpServletRequest request) {
    challenges.requireIpRate("reset-verify", request.getRemoteAddr(), 150, 15);
    return this.userFacade.verify(verifyOTPRequest);
  }

  @GetMapping("/profile")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  @SecurityRequirement(name = SecurityConfig.SECURITY_REQUIREMENT)
  @PreAuthorize("isAuthenticated()")
  public BaseResponse<UserDetailResponse> findProfile() {
    return this.userFacade.findProfile();
  }

  @GetMapping("/detail/{id}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  @SecurityRequirement(name = SecurityConfig.SECURITY_REQUIREMENT)
  @PreAuthorize("hasAnyAuthority('ROLE_SYSTEM_ADMIN','ROLE_COMPANY_ADMIN')")
  public BaseResponse<UserDetailResponse> findDetailById(@PathVariable Long id) {
    return this.userFacade.findDetailById(id);
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("isAuthenticated()")
  public BaseResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
      HttpServletResponse response) {
    passwords.change(request);
    sessions.clearBrowserCookies(response);
    return BaseResponse.ok();
  }

  @PostMapping(value = "/avatar", consumes = "multipart/form-data", produces = "application/json")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Upload image",
      tags = {"User APIs"})
  @SecurityRequirement(name = SecurityConfig.SECURITY_REQUIREMENT)
  @PreAuthorize("isAuthenticated()")
  @SneakyThrows
  public BaseResponse<String> uploadImage(@RequestPart("image") MultipartFile image) {
    try {
      return this.userFacade.uploadFile(ImageFileValidator.validate(image));
    } catch (IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
    }
  }

  @PutMapping("/profile")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  @SecurityRequirement(name = SecurityConfig.SECURITY_REQUIREMENT)
  @PreAuthorize("isAuthenticated()")
  public BaseResponse<Void> updateProfile(
      @Valid @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {
    return this.userFacade.updateProfile(updateUserProfileRequest);
  }
}
