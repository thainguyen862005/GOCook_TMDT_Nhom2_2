package com.james.LMS.request;

import io.swagger.v3.oas.annotations.Hidden;
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

public class UpsertUserRequest {
  @Hidden private Long id;

  @NotBlank
  @Size(min = 2, max = 80) private String username;

  @Email(message = "Email invalid")
  @NotBlank
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
  private String password;
}
