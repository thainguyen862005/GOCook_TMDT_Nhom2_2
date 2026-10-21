package com.james.LMS.admin.dto;

import com.james.LMS.enums.RoleEnum;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserView {
  private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private Long id;
  private String username;
  private String email;
  private RoleEnum role;
  private List<RoleEnum> roles;
  private String roleLabel;
  private boolean active;
  private Long createdAt;

  public String getRoleName() {
    return role == null ? RoleEnum.USER.name() : role.name();
  }

  public String getCreatedAtDisplay() {
    if (createdAt == null) {
      return "—";
    }
    return Instant.ofEpochMilli(createdAt).atZone(APP_ZONE).format(DISPLAY_FORMAT);
  }
}
