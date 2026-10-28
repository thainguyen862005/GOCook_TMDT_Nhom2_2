package com.james.LMS.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class ForgotPasswordRequest {
  @Email(message = "Email is valid")
  @NotBlank
  @Size(max = 254)
  private String email;
}
