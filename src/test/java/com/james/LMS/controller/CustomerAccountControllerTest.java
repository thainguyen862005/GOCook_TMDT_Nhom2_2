package com.james.LMS.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.james.LMS.config.SecurityConfig;
import com.james.LMS.entity.Role;
import com.james.LMS.entity.User;
import com.james.LMS.enums.RoleEnum;
import com.james.LMS.facade.UserFacade;
import com.james.LMS.request.CustomerAddressRequest;
import com.james.LMS.response.BaseResponse;
import com.james.LMS.response.CustomerAddressResponse;
import com.james.LMS.service.*;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({UserController.class, CustomerAddressController.class})
@Import(SecurityConfig.class)
class CustomerAccountControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean UserFacade facade;
  @MockitoBean UserService users;
  @MockitoBean RoleService roles;
  @MockitoBean JwtService jwt;
  @MockitoBean CacheService cache;
  @MockitoBean CustomerAddressService addresses;
  // Mock new authentication collaborators required by the UserController constructor.
  @MockitoBean AuthSessionService sessions;
  @MockitoBean AccountPasswordService passwords;
  @MockitoBean AuthChallengeService challenges;
  final String path = "/api/v1/users/addresses";
  final Cookie cookie = new Cookie("access-token", "c06-token");
  final String trustedOrigin = "http://localhost:5173";
  final String valid = """
      {"userId":999,"label":"Nhà","recipientName":"Khách","phone":"0901234567",
       "addressLine":"12 Đường A","ward":"","district":"Quận 1","city":"TP.HCM",
       "note":"","isDefault":true,"version":0}
      """;

  @BeforeEach
  void authenticate() {
    when(jwt.validateTokenForPurpose("c06-token", "access")).thenReturn(true);
    when(jwt.getEmailFromJwtToken("c06-token")).thenReturn("c06@example.test");
    when(cache.hasKey("ACCESS_TOKEN_c06@example.test")).thenReturn(true);
    when(cache.retrieve("ACCESS_TOKEN_c06@example.test")).thenReturn("c06-token");
    when(users.findByEmail("c06@example.test")).thenReturn(Optional.of(
        User.builder().id(7L).username("Khách").email("c06@example.test").password("unused").build()));
    when(roles.findAllByUserId(7L)).thenReturn(List.of(Role.builder().roleName(RoleEnum.USER).build()));
  }

  @Test
  void allAddressEndpointsRequireLogin() throws Exception {
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized());
    mvc.perform(put(path + "/2").contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized());
    mvc.perform(delete(path + "/2")).andExpect(status().isUnauthorized());
    mvc.perform(patch(path + "/2/default")).andExpect(status().isUnauthorized());
    verifyNoInteractions(addresses);
  }

  @Test
  void ownershipComesFromAuthenticatedPrincipalNotPayload() throws Exception {
    var result = new CustomerAddressResponse(2L, "Nhà", "Khách", "0901234567", "12 Đường A",
        "", "Quận 1", "TP.HCM", "", true, 0L, 1L, 1L);
    when(addresses.create(eq(7L), any())).thenReturn(result);
    mvc.perform(post(path).cookie(cookie).header("Origin", trustedOrigin).contentType(MediaType.APPLICATION_JSON).content(valid))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.metadata.id").value(2))
        .andExpect(jsonPath("$.metadata.isDefault").value(true));
    verify(addresses).create(eq(7L), any(CustomerAddressRequest.class));
    when(addresses.list(7L)).thenReturn(List.of(result));
    mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata[0].isDefault").value(true));
    mvc.perform(put(path + "/2").cookie(cookie).header("Origin", trustedOrigin).contentType(MediaType.APPLICATION_JSON).content(valid))
        .andExpect(status().isOk());
    mvc.perform(patch(path + "/2/default").cookie(cookie).header("Origin", trustedOrigin)).andExpect(status().isOk());
    mvc.perform(delete(path + "/2").cookie(cookie).header("Origin", trustedOrigin)).andExpect(status().isOk());
    verify(addresses).update(eq(7L), eq(2L), any());
    verify(addresses).setDefault(7L, 2L);
    verify(addresses).delete(7L, 2L);
  }

  @Test
  void validatesBeforeCallingAddressService() throws Exception {
    mvc.perform(post(path).cookie(cookie).header("Origin", trustedOrigin).contentType(MediaType.APPLICATION_JSON)
        .content(valid.replace("0901234567", "invalid")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(addresses);
  }

  @Test
  void profileValidationDoesNotBreakUsernameOnlyClients() throws Exception {
    when(facade.updateProfile(any())).thenReturn(BaseResponse.ok());
    mvc.perform(put("/api/v1/users/profile").cookie(cookie).header("Origin", trustedOrigin).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"Khách\"}"))
        .andExpect(status().isOk());
    mvc.perform(put("/api/v1/users/profile").cookie(cookie).header("Origin", trustedOrigin).contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"  \"}"))
        .andExpect(status().isBadRequest());
    verify(facade, times(1)).updateProfile(any());
  }

  @Test
  void rejectsAuthenticatedCookieWritesWithoutTrustedOrigin() throws Exception {
    mvc.perform(post(path).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(valid))
        .andExpect(status().isForbidden());
    verifyNoInteractions(addresses);
  }

  @Test
  void chefWithoutCustomerRoleCannotManageCustomerAddresses() throws Exception {
    when(roles.findAllByUserId(7L)).thenReturn(List.of(Role.builder().roleName(RoleEnum.INSTRUCTOR).build()));
    mvc.perform(get(path).cookie(cookie)).andExpect(status().isForbidden());
    verifyNoInteractions(addresses);
  }
}
