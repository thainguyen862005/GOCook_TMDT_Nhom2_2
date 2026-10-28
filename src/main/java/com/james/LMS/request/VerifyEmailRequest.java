package com.james.LMS.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VerifyEmailRequest {
  @Email(message = "Email invalid")
  @NotBlank
  @Size(max = 254)
  private String email;

  @NotBlank
  @Pattern(regexp = "\\d{6}", message = "OTP must contain 6 digits")
  private String otp;
}
