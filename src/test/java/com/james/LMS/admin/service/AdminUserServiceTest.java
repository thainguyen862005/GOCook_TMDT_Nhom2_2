package com.james.LMS.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.admin.repository.AdminUserRepository;
import com.james.LMS.entity.Role;
import com.james.LMS.entity.User;
import com.james.LMS.enums.RoleEnum;
import com.james.LMS.repository.RoleRepository;
import com.james.LMS.service.CacheService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
  @Mock AdminUserRepository users;
  @Mock RoleRepository roles;
  @Mock CacheService cache;
  @InjectMocks AdminUserService service;

  @Test
  void cannotLockSelf() {
    User admin = user(1L);
    when(users.lockAdminRole()).thenReturn(1L);
    when(users.findById(1L)).thenReturn(Optional.of(admin));
    when(roles.findAllByUserId(1L)).thenReturn(List.of(role(RoleEnum.ADMIN)));
    assertThrows(IllegalArgumentException.class,
        () -> service.update(1L, "Admin", List.of(RoleEnum.ADMIN), false, 1L));
    verify(users, never()).updateActive(anyLong(), anyBoolean());
  }

  @Test
  void cannotDemoteLastActiveAdmin() {
    User admin = user(2L);
    when(users.lockAdminRole()).thenReturn(1L);
    when(users.findById(2L)).thenReturn(Optional.of(admin));
    when(roles.findAllByUserId(2L)).thenReturn(List.of(role(RoleEnum.ADMIN)));
    when(users.countActiveByRole("ADMIN")).thenReturn(1L);
    assertThrows(IllegalArgumentException.class,
        () -> service.update(2L, "Admin", List.of(RoleEnum.USER), true, 1L));
    verify(users, never()).saveAndFlush(any(User.class));
  }

  @Test
  void renamingKeepsBothRoles() {
    User chef = user(3L);
    chef.getRoles().add(role(RoleEnum.USER));
    chef.getRoles().add(role(RoleEnum.INSTRUCTOR));
    when(users.lockAdminRole()).thenReturn(1L);
    when(users.findById(3L)).thenReturn(Optional.of(chef));
    when(roles.findAllByUserId(3L))
        .thenReturn(List.of(role(RoleEnum.USER), role(RoleEnum.INSTRUCTOR)));
    service.update(3L, "Chef đã đổi tên", List.of(RoleEnum.USER, RoleEnum.INSTRUCTOR), true, 1L);
    assertEquals("Chef đã đổi tên", chef.getUsername());
    assertEquals(2, chef.getRoles().size());
    verify(users).saveAndFlush(chef);
    verifyNoInteractions(cache);
  }

  @Test
  void invalidStatusFilterIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> service.search("", "", "invalid", 0, 10));
  }

  @Test
  void nullRoleIsRejectedWithValidationError() {
    List<RoleEnum> withNull = new ArrayList<>();
    withNull.add(RoleEnum.USER);
    withNull.add(null);

    User user = user(4L);
    when(users.lockAdminRole()).thenReturn(1L);
    when(users.findById(4L)).thenReturn(Optional.of(user));

    assertThrows(IllegalArgumentException.class,
        () -> service.update(4L, "Customer", withNull, true, 1L));

    verify(users, never()).saveAndFlush(any(User.class));
  }

  private User user(long id) {
    return User.builder().id(id).username("Demo Admin").email("demo" + id + "@gocook.test")
        .password("BCryptHash").roles(new ArrayList<>()).build();
  }

  private Role role(RoleEnum role) {
    return Role.builder().roleName(role).build();
  }
}
