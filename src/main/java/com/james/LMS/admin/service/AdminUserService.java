package com.james.LMS.admin.service;

import com.james.LMS.admin.dto.AdminUserView;
import com.james.LMS.admin.exception.AdminResourceNotFoundException;
import com.james.LMS.admin.repository.AdminUserRepository;
import com.james.LMS.admin.util.AdminPaging;
import com.james.LMS.entity.Role;
import com.james.LMS.entity.User;
import com.james.LMS.enums.RoleEnum;
import com.james.LMS.enums.TokenType;
import com.james.LMS.repository.RoleRepository;
import com.james.LMS.service.CacheService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
  private final AdminUserRepository userRepository;
  private final RoleRepository roleRepository;
  private final CacheService cacheService;

  @Transactional(readOnly = true)
  public Page<AdminUserView> search(String query, String role, String status, int page, int size) {
    RoleEnum roleFilter = parseRole(role);
    Boolean activeFilter = parseActive(status);
    return userRepository.search(
        query == null ? "" : query.trim(),
        roleFilter == null ? null : roleFilter.name(),
        activeFilter, AdminPaging.of(page, size)).map(this::toView);
  }

  @Transactional(readOnly = true)
  public AdminUserView get(Long id) {
    return toView(findUser(id));
  }

  @Transactional
  public void update(Long id, String username, List<RoleEnum> requestedRoles,
                     boolean active, Long currentAdminId) {
    lockAdminRole();
    User user = findUser(id);
    String cleanUsername = requireUsername(username);
    Set<RoleEnum> nextRoles = normalizeRoles(requestedRoles);
    Set<RoleEnum> oldRoles = currentRoles(user.getId());
    protectAdmin(user, oldRoles, nextRoles, active, currentAdminId);

    boolean changedRoles = !oldRoles.equals(nextRoles);
    boolean changedActive = user.isActive() != active;
    user.changeUsername(cleanUsername);

    if (changedRoles) {
      List<Role> replacements = new ArrayList<>();
      for (RoleEnum type : nextRoles) {
        replacements.add(roleRepository.findRoleByRoleName(type).orElseThrow(
            () -> new IllegalArgumentException("Vai trò chưa tồn tại: " + type)));
      }
      user.getRoles().clear();
      user.getRoles().addAll(replacements);
    }
    userRepository.saveAndFlush(user);
    if (changedActive) {
      userRepository.updateActive(id, active);
    }
    if (changedRoles || !active) {
      revokeSessions(user.getEmail());
    }
  }

  @Transactional
  public void toggle(Long id, Long currentAdminId) {
    lockAdminRole();
    User user = findUser(id);
    Set<RoleEnum> roles = currentRoles(user.getId());
    boolean nextActive = !user.isActive();
    protectAdmin(user, roles, roles, nextActive, currentAdminId);
    userRepository.updateActive(id, nextActive);
    if (!nextActive) {
      revokeSessions(user.getEmail());
    }
  }

  private void lockAdminRole() {
    if (userRepository.lockAdminRole() == null) {
      throw new IllegalStateException("Thiếu vai trò ADMIN trong dữ liệu hệ thống.");
    }
  }

  private void protectAdmin(User target, Set<RoleEnum> oldRoles,
                            Set<RoleEnum> nextRoles, boolean nextActive, Long actorId) {
    boolean removingAdmin = oldRoles.contains(RoleEnum.ADMIN)
        && !nextRoles.contains(RoleEnum.ADMIN);
    boolean disablingAdmin = oldRoles.contains(RoleEnum.ADMIN) && !nextActive;

    if (target.getId().equals(actorId) && (removingAdmin || !nextActive)) {
      throw new IllegalArgumentException("Không thể tự khóa hoặc gỡ quyền quản trị của chính mình.");
    }
    if (target.isActive() && (removingAdmin || disablingAdmin)
        && userRepository.countActiveByRole(RoleEnum.ADMIN.name()) <= 1) {
      throw new IllegalArgumentException("Hệ thống phải còn ít nhất một Admin đang hoạt động.");
    }
  }

  private User findUser(Long id) {
    return userRepository.findById(id).orElseThrow(
        () -> new AdminResourceNotFoundException("Không tìm thấy người dùng."));
  }

  private Set<RoleEnum> currentRoles(Long userId) {
    Set<RoleEnum> roles = EnumSet.noneOf(RoleEnum.class);
    roleRepository.findAllByUserId(userId).forEach(role -> roles.add(role.getRoleName()));
    return roles;
  }

  private AdminUserView toView(User user) {
    List<RoleEnum> roles = currentRoles(user.getId()).stream()
        .sorted(Comparator.comparingInt(this::rolePriority)).toList();
    RoleEnum primary = roles.isEmpty() ? RoleEnum.USER : roles.get(0);
    return AdminUserView.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .role(primary)
        .roles(roles)
        .roleLabel(roles.isEmpty() ? "Chưa phân quyền" :
            roles.stream().map(this::roleLabel).collect(Collectors.joining(", ")))
        .active(user.isActive())
        .createdAt(user.getCreatedAt())
        .build();
  }

  public List<RoleEnum> roles() {
    return List.of(RoleEnum.USER, RoleEnum.INSTRUCTOR, RoleEnum.ADMIN, RoleEnum.COMPANY_ADMIN);
  }

  public String roleLabel(RoleEnum role) {
    return switch (role) {
      case USER -> "Khách hàng";
      case INSTRUCTOR -> "Đầu bếp";
      case ADMIN -> "Quản trị viên";
      case COMPANY_ADMIN -> "Quản trị công ty";
    };
  }

  private int rolePriority(RoleEnum role) {
    return switch (role) {
      case ADMIN -> 0;
      case COMPANY_ADMIN -> 1;
      case INSTRUCTOR -> 2;
      case USER -> 3;
    };
  }

  private Set<RoleEnum> normalizeRoles(List<RoleEnum> roles) {
    if (roles == null || roles.isEmpty() || roles.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("Tài khoản cần có ít nhất một vai trò hợp lệ.");
    }
    return EnumSet.copyOf(roles);
  }

  private RoleEnum parseRole(String role) {
    if (role == null || role.isBlank()) return null;
    try {
      return RoleEnum.valueOf(role.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Bộ lọc vai trò không hợp lệ.");
    }
  }

  private Boolean parseActive(String status) {
    if (status == null || status.isBlank()) return null;
    if ("active".equalsIgnoreCase(status)) return true;
    if ("inactive".equalsIgnoreCase(status)) return false;
    throw new IllegalArgumentException("Bộ lọc trạng thái tài khoản không hợp lệ.");
  }

  private String requireUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Tên hiển thị không được để trống.");
    }
    String value = username.trim();
    if (value.length() > 255) {
      throw new IllegalArgumentException("Tên hiển thị không được dài quá 255 ký tự.");
    }
    return value;
  }

  private void revokeSessions(String email) {
    cacheService.delete(String.format(TokenType.ACCESS_TOKEN.getCacheKeyTemplate(), email));
    cacheService.delete(String.format(TokenType.REFRESH_TOKEN.getCacheKeyTemplate(), email));
  }
}
