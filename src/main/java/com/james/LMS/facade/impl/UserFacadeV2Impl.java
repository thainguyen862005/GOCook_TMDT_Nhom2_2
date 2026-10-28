package com.james.LMS.facade.impl;

import com.james.LMS.entity.User;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.facade.UserFacadeV2;
import com.james.LMS.request.LoginRequest;
import com.james.LMS.response.BaseResponse;
import com.james.LMS.service.AuthChallengeService;
import com.james.LMS.service.AuthSessionService;
import com.james.LMS.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserFacadeV2Impl implements UserFacadeV2 {
  private final UserService users;
  private final PasswordEncoder encoder;
  private final AuthChallengeService challenges;
  private final AuthSessionService sessions;

  @Override
  public BaseResponse<Void> login(LoginRequest request, HttpServletResponse response) {
    String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
    challenges.requireLoginAllowed(email);
    User user = users.findByEmail(email).orElse(null);
    // Timing noise is reduced by verifying against a valid fallback bcrypt hash.
    String hash = user == null ?
        "$2a$10$N9qo8uLOickgx2ZMRZoMye1X6v3lmT4HBkFfGHwuFROpS4dF7S3K2" : user.getPassword();
    boolean matches = encoder.matches(request.getPassword(), hash);
    if (user == null || !user.isActive() || !user.isPasswordLoginEnabled() || !matches) {
      challenges.failedLogin(email);
      throw new BadCredentialsException(ErrorCode.INVALID_CREDENTIALS.getMessage());
    }
    challenges.clearLoginFailures(email);
    sessions.create(email, response);
    return BaseResponse.ok();
  }
}
