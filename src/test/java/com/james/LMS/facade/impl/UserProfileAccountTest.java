package com.james.LMS.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.User;
import com.james.LMS.repository.UserAuthIdentityRepository;
import com.james.LMS.request.UpdateUserProfileRequest;
import com.james.LMS.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserProfileAccountTest {
  @Mock UserService users;
  @Mock UserAuthIdentityRepository identities;
  @InjectMocks UserFacadeImpl facade;
  User user;

  @BeforeEach
  void authenticate() {
    user = User.builder().id(7L).username("Khách").email("c06@example.test")
        .phone("0901234567").dietaryPreferences("Ít cay").allergyInfo("Đậu phộng")
        .createdAt(1700000000000L).build();
    var authorities = List.<org.springframework.security.core.GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER"));
    var principal = SecurityUserDetails.build(user, authorities);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, authorities));
    when(users.findByEmail("c06@example.test")).thenReturn(Optional.of(user));
  }

  @AfterEach
  void clearSecurity() { SecurityContextHolder.clearContext(); }

  @Test
  void ownProfileContainsNewFields() {
    var result = facade.findProfile().getMetadata();
    assertThat(result.getPhone()).isEqualTo("0901234567");
    assertThat(result.getDietaryPreferences()).isEqualTo("Ít cay");
    assertThat(result.getAllergyInfo()).isEqualTo("Đậu phộng");
  }

  @Test
  void usernameOnlyUpdatePreservesOptionalFieldsAndIgnoresPayloadId() {
    facade.updateProfile(new UpdateUserProfileRequest(999L, " Tên mới ", null, null));
    assertThat(user.getId()).isEqualTo(7L);
    assertThat(user.getUsername()).isEqualTo("Tên mới");
    assertThat(user.getPhone()).isEqualTo("0901234567");
    assertThat(user.getAllergyInfo()).isEqualTo("Đậu phộng");
    verify(users).save(user);
  }

  @Test
  void fullProfileUpdateCanClearOptionalInformation() {
    facade.updateProfile(new UpdateUserProfileRequest(null, "Khách", "", " Ăn chay ", "", null, null));
    assertThat(user.getPhone()).isEmpty();
    assertThat(user.getDietaryPreferences()).isEqualTo("Ăn chay");
    assertThat(user.getAllergyInfo()).isEmpty();
  }
}
