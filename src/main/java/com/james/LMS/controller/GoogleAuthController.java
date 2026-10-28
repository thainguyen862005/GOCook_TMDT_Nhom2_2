package com.james.LMS.controller;

import com.james.LMS.response.BaseResponse;
import com.james.LMS.service.AuthChallengeService;
import com.james.LMS.service.AuthSessionService;
import com.james.LMS.service.GoogleSocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/users/google")
@RequiredArgsConstructor
public class GoogleAuthController {
  private final AuthChallengeService challenges;
  private final GoogleSocialAuthService google;
  private final AuthSessionService sessions;
  public record GoogleCredential(@NotBlank @Size(max = 8192) String credential) {}
  public record NewPassword(@NotBlank @Size(min = 12, max = 128) String password,
                            @NotBlank String confirmPassword) {}
  public record GoogleNonce(String nonce) {}

  @GetMapping("/nonce")
  public BaseResponse<GoogleNonce> nonce(HttpServletRequest request) {
    challenges.requireIpRate("google-nonce", request.getRemoteAddr(), 120, 15);
    return BaseResponse.build(new GoogleNonce(challenges.issueGoogleNonce()), true);
  }

  @PostMapping("/sign-in")
  @ResponseStatus(HttpStatus.OK)
  public BaseResponse<Void> signIn(@Valid @RequestBody GoogleCredential request,
      HttpServletRequest httpRequest, HttpServletResponse response) {
    challenges.requireIpRate("google-login", httpRequest.getRemoteAddr(), 120, 15);
    google.signIn(google.verified(request.credential()), response);
    return BaseResponse.ok();
  }

  @PostMapping("/link")
  @PreAuthorize("isAuthenticated()")
  public BaseResponse<Void> link(@Valid @RequestBody GoogleCredential request) {
    google.link(google.verified(request.credential()));
    return BaseResponse.ok();
  }

  @PostMapping("/set-password")
  @PreAuthorize("isAuthenticated()")
  public BaseResponse<Void> setPassword(@Valid @RequestBody NewPassword request,
      HttpServletResponse response) {
    google.setInitialPassword(request.password(), request.confirmPassword());
    sessions.clearBrowserCookies(response);
    return BaseResponse.ok();
  }
}
