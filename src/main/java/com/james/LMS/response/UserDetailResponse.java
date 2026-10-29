package com.james.LMS.response;

import java.time.LocalDate;
import java.util.List;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class UserDetailResponse {
  private Long id;
  private String username;
  private String email;
  private Boolean emailVerified;
  private Boolean passwordLoginEnabled;
  private Boolean googleLinked;
  private String avatarUrl;
  private String phone;
  private String defaultAddress;
  private String dietaryPreferences;
  private String allergyInfo;
  private Boolean isInstructor;
  private String instructorName;
  private String instructorAbout;
  private LocalDate createdAt;
  private Long channelId;
  private List<String> roles;
}
