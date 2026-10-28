package com.james.LMS.request;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class UpdateUserProfileRequest {
  @Hidden private Long id;
  @NotBlank @Size(min = 2, max = 80) private String username;
  @Pattern(regexp = "^$|\\+?[0-9]{9,15}", message = "Số điện thoại không hợp lệ")
  private String phone;
  @Size(max = 500)
  private String defaultAddress;
  private String instructorName;
  private String instructorAbout;
}
