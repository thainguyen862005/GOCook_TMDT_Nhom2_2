package com.james.LMS.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {
  @NotBlank private String resetPasswordToken;

  @NotBlank
  @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
  private String newPassword;

  @NotBlank private String confirmPassword;
}
