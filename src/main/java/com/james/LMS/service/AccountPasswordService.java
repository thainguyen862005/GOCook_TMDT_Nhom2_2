package com.james.LMS.service;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.User;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.EntityNotFoundException;
import com.james.LMS.request.ChangePasswordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountPasswordService {
  private final UserService users;
  private final PasswordEncoder encoder;
  private final AuthSessionService sessions;

  @Transactional
  public void change(ChangePasswordRequest request) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof SecurityUserDetails principal)) {
      throw new BadCredentialsException("Authentication required");
    }
    User user = users.findByEmail(principal.getUsername())
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    if (!encoder.matches(request.currentPassword(), user.getPassword())) {
      throw new BadCredentialsException(ErrorCode.INVALID_CREDENTIALS.getMessage());
    }
    if (!request.newPassword().equals(request.confirmPassword())
        || encoder.matches(request.newPassword(), user.getPassword())) {
      throw new IllegalArgumentException("Mật khẩu mới không hợp lệ hoặc trùng mật khẩu hiện tại.");
    }
    user.changePassword(encoder.encode(request.newPassword()));
    users.save(user);
    sessions.revokeAll(user.getEmail());
  }
}
