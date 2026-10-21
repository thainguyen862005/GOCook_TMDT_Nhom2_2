package com.james.LMS.controller;

import com.james.LMS.config.SecurityConfig;
import com.james.LMS.media.ImageFileValidator;
import org.springframework.web.server.ResponseStatusException;
import com.james.LMS.facade.UserFacade;
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
  public BaseResponse<Void> signUp(@Valid @RequestBody UpsertUserRequest upsertUserRequest) {
    return this.userFacade.signUp(upsertUserRequest);
  }


  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<Void> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest verifyEmailRequest) {
    return this.userFacade.verifyEmail(verifyEmailRequest);
  }

  @PostMapping({"/resend-verification", "/request-email-verification"})
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<Void> resendVerification(
      @Valid @RequestBody ResendVerificationRequest resendVerificationRequest) {
    return this.userFacade.resendVerification(resendVerificationRequest);
  }

  @PostMapping("/refresh-token")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<RefreshTokenResponse> refreshToken(
      @RequestBody RefreshTokenRequest refreshTokenRequest) {
    return this.userFacade.refreshToken(refreshTokenRequest);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  @SecurityRequirement(name = SecurityConfig.SECURITY_REQUIREMENT)
  @PreAuthorize("isAuthenticated()")
  public BaseResponse<Void> logout(HttpServletResponse response) {
    BaseResponse<Void> result = this.userFacade.logout();

    clearCookie(response, SecurityConfig.COOKIE_SECURITY_NAME, true);
    clearCookie(response, SecurityConfig.COOKIE_REFRESH_TOKEN_NAME, true);
    clearCookie(response, SecurityConfig.VALIDATE_LOGIN, false);
    clearCookie(response, REMEMBER_ME_COOKIE, false);

    return result;
  }

  private void clearCookie(HttpServletResponse response, String name, boolean httpOnly) {
    ResponseCookie cookie =
        ResponseCookie.from(name, "")
            .httpOnly(httpOnly)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(0)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
      @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
    return this.userFacade.forgotPassword(forgotPasswordRequest);
  }

  @PostMapping("/verify-otp")
  @ResponseStatus(HttpStatus.OK)
  @Operation(tags = {"User APIs"})
  public BaseResponse<VerifyOTPResponse> verify(
      @Valid @RequestBody VerifyOTPRequest verifyOTPRequest) {
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
  @PreAuthorize("hasAuthority('ROLE_USER')")
  public BaseResponse<UserDetailResponse> findDetailById(@PathVariable Long id) {
    return this.userFacade.findDetailById(id);
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
  @PreAuthorize("hasAuthority('ROLE_USER')")
  public BaseResponse<Void> updateProfile(
      @RequestBody UpdateUserProfileRequest updateUserProfileRequest) {
    return this.userFacade.updateProfile(updateUserProfileRequest);
  }
}
