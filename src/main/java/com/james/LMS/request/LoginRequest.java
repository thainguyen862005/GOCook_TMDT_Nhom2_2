package com.james.LMS.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@AllArgsConstructor
@NoArgsConstructor

public class LoginRequest {
  @Email(message = "Email invalid")
  @NotBlank
  @Schema(defaultValue = "23130075@st.hcmuaf.edu.vn")
  @Size(max = 254)
  private String email;

  @NotBlank @Size(max = 128) private String password;
}
