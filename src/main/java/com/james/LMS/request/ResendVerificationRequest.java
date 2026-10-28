package com.james.LMS.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResendVerificationRequest {
  @Email(message = "Email invalid")
  @NotBlank
  @Size(max = 254)
  private String email;
}
