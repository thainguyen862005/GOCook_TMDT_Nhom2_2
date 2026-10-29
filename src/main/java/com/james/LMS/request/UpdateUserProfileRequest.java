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
  @Size(max = 500)
  private String dietaryPreferences;
  @Size(max = 500)
  private String allergyInfo;
  private String instructorName;
  private String instructorAbout;

  // Backward-compatible constructor used by username-only profile clients/tests.
  public UpdateUserProfileRequest(Long id, String username, String phone, String defaultAddress) {
    this(id, username, phone, defaultAddress, null, null, null, null);
  }

  // Preserve the previous six-argument constructor for existing callers.
  public UpdateUserProfileRequest(Long id, String username, String phone,
      String defaultAddress, String instructorName, String instructorAbout) {
    this(id, username, phone, defaultAddress, null, null, instructorName, instructorAbout);
  }

  // Customer-account tests supply dietary/allergy fields without a default address.
  public UpdateUserProfileRequest(Long id, String username, String phone,
      String dietaryPreferences, String allergyInfo,
      String instructorName, String instructorAbout) {
    this(id, username, phone, null, dietaryPreferences, allergyInfo,
        instructorName, instructorAbout);
  }
}
