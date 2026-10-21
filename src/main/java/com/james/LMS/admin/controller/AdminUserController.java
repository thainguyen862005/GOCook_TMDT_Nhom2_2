package com.james.LMS.admin.controller;

import com.james.LMS.admin.dto.AdminUserView;
import com.james.LMS.admin.dto.request.UpdateUserRequest;
import com.james.LMS.admin.dto.response.AdminUsersResponse;
import com.james.LMS.admin.service.AdminUserService;
import com.james.LMS.config.SecurityUserDetails;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import com.james.LMS.admin.operations.AdminAuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService userService;
  private final AdminAuditService audit;

  @GetMapping
  public AdminUsersResponse search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String role,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Page<AdminUserView> result = userService.search(q, role, status, page, size);
    return AdminUsersResponse.from(result, userService.roles());
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    return Map.of("user", userService.get(id), "roles", userService.roles());
  }

  @PutMapping("/{id}")
  @Transactional
  public Map<String, Object> update(
      @PathVariable Long id,
      @RequestBody UpdateUserRequest request,
      @AuthenticationPrincipal SecurityUserDetails currentUser) {
    userService.update(id, request.username(), request.roles(), request.active(), currentUser.getId());
    audit.record(currentUser.getId(), "USER_UPDATE", "USER", id, "Cập nhật tài khoản và quyền");
    return Map.of("message", "Đã cập nhật người dùng.", "user", userService.get(id));
  }

  @PatchMapping("/{id}/toggle")
  @Transactional
  public Map<String, Object> toggle(
      @PathVariable Long id,
      @AuthenticationPrincipal SecurityUserDetails currentUser) {
    userService.toggle(id, currentUser.getId());
    audit.record(currentUser.getId(), "USER_TOGGLE", "USER", id, "Thay đổi trạng thái tài khoản");
    return Map.of("message", "Đã cập nhật trạng thái tài khoản.", "user", userService.get(id));
  }
}
