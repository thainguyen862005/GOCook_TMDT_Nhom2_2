package com.james.LMS.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.entity.User;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.service.CacheService;
import com.james.LMS.service.CloudinaryService;
import com.james.LMS.service.JwtService;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${GOCOOK_MENU_TEST_URL}",
      "spring.datasource.username=app_user",
      "spring.datasource.password=${GOCOOK_MENU_TEST_PASSWORD:app_password}",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.rabbitmq.dynamic=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false",
      "cloudinary.cloudinary-name=menu-test",
      "cloudinary.api-key=unused",
      "cloudinary.api-secret=unused",
      "spring.mail.username=unused",
      "spring.mail.password=unused",
      "logging.level.com.james.LMS.filter.LoggingFilter=OFF",
      "jwt.secretKey=c2VsbGVyLW1lbnUtdGVzdC1vbmx5LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24tc2VsbGVyLW1lbnUtdGVzdC1vbmx5"
    })
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "GOCOOK_MENU_TEST_URL", matches = ".+gocook_menu_test_.+")
class SellerScheduleJwtTest {
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @MockitoBean CacheService cache;
  @MockitoBean CloudinaryService cloudinary;
  final Map<String, String> tokens = new HashMap<>();
  Cookie chef;
  Cookie other;
  Cookie customer;
  Cookie admin;
  final String path = "/api/seller/schedules";
  final String valid =
      "{\"serviceDate\":\"2027-01-05\",\"startTime\":\"09:00\",\"endTime\":\"12:00\",\"available\":true}";

  @BeforeEach
  void setup() {
    chef = account("INSTRUCTOR");
    other = account("INSTRUCTOR");
    customer = account("USER");
    admin = account("SYSTEM_ADMIN");
    when(cache.hasKey(anyString())).thenAnswer(call -> tokens.containsKey(call.getArgument(0)));
    when(cache.retrieve(anyString())).thenAnswer(call -> tokens.get(call.getArgument(0)));
  }

  Cookie account(String role) {
    String email = UUID.randomUUID() + "@example.invalid";
    var user =
        users.saveAndFlush(
            User.builder()
                .username("Schedule JWT test")
                .email(email)
                .password("unused")
                .emailVerified(true)
                .build());
    jdbc.update(
        "insert into user_roles(user_id,role_id) select ?,id from roles where role_name=?",
        user.getId(),
        role);
    String token = jwt.generateAccessToken(email);
    tokens.put("ACCESS_TOKEN_" + email, token);
    return new Cookie("access-token", token);
  }

  @Test
  void realJwtAndRoleGuardsProtectAllOperations() throws Exception {
    for (var actor : new Cookie[] {customer, admin}) {
      mvc.perform(get(path + "?from=2027-01-05&to=2027-01-11").cookie(actor).header("Origin", "http://localhost:5173"))
          .andExpect(status().isForbidden());
      mvc.perform(post(path).cookie(actor).header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(valid))
          .andExpect(status().isForbidden());
      mvc.perform(
              patch(path + "/1/availability")
                  .cookie(actor).header("Origin", "http://localhost:5173")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"available\":false,\"version\":0}"))
          .andExpect(status().isForbidden());
      mvc.perform(delete(path + "/1?version=0").cookie(actor).header("Origin", "http://localhost:5173")).andExpect(status().isForbidden());
    }
    mvc.perform(get(path + "?from=2027-01-05&to=2027-01-11")).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path)
                .cookie(new Cookie("access-token", "bad"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isUnauthorized());
    tokens.clear();
    mvc.perform(get(path + "?from=2027-01-05&to=2027-01-11").cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roundTripMatchesFrontendStringsAndOwnershipAndVersionContract() throws Exception {
    var response =
        mvc.perform(post(path).cookie(chef).header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.serviceDate").value("2027-01-05"))
            .andExpect(jsonPath("$.startTime").value("09:00:00"))
            .andReturn();
    var slot = mapper.readTree(response.getResponse().getContentAsString());
    long id = slot.get("id").asLong();
    long version = slot.get("version").asLong();
    mvc.perform(get(path + "?from=2027-01-05&to=2027-01-11").cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots.length()").value(1))
        .andExpect(jsonPath("$.bookings.length()").value(0));
    mvc.perform(get(path + "?from=2027-01-05&to=2027-01-11").cookie(other).header("Origin", "http://localhost:5173"))
        .andExpect(jsonPath("$.slots.length()").value(0));
    mvc.perform(delete(path + "/" + id + "?version=" + version).cookie(other).header("Origin", "http://localhost:5173"))
        .andExpect(status().isNotFound());
    var updated =
        mvc.perform(
                patch(path + "/" + id + "/availability")
                    .cookie(chef).header("Origin", "http://localhost:5173")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"available\":false,\"version\":" + version + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andReturn();
    long newVersion =
        mapper.readTree(updated.getResponse().getContentAsString()).get("version").asLong();
    assertThat(newVersion).isGreaterThan(version);
    mvc.perform(delete(path + "/" + id + "?version=" + version).cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isConflict());
    mvc.perform(delete(path + "/" + id + "?version=" + newVersion).cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isNoContent());
  }

  @Test
  void realBookingTimesAreStringsAndPreventClosingTheSlot() throws Exception {
    var response =
        mvc.perform(post(path).cookie(chef).header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isCreated())
            .andReturn();
    var slot = mapper.readTree(response.getResponse().getContentAsString());
    Long chefId =
        users.findByEmail(jwt.getEmailFromJwtToken(chef.getValue())).orElseThrow().getId();
    Long customerId =
        users.findByEmail(jwt.getEmailFromJwtToken(customer.getValue())).orElseThrow().getId();
    Long orderId =
        jdbc.queryForObject(
            "insert into"
                + " orders(order_code,customer_name,product_name,guest_count,scheduled_at,total_amount,status,chef_id,customer_id)"
                + " values (?, 'test', 'test', 2, '2027-01-05 09:00', 100000,'CONFIRMED',?,?)"
                + " returning id",
            Long.class,
            UUID.randomUUID().toString(),
            chefId,
            customerId);
    jdbc.update(
        "insert into chef_bookings(order_id,chef_id,starts_at,ends_at) values(?,?, '2027-01-05"
            + " 09:00', '2027-01-05 12:00')",
        orderId,
        chefId);
    mvc.perform(get(path + "?from=2027-01-05&to=2027-01-05").cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookings[0].startsAt").value("2027-01-05T09:00:00"))
        .andExpect(jsonPath("$.bookings[0].orderId").value(orderId));
    mvc.perform(
            patch(path + "/" + slot.get("id").asLong() + "/availability")
                .cookie(chef).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false,\"version\":" + slot.get("version").asLong() + "}"))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsPastCreationAndReopeningViaAuthenticatedApiButAllowsHistory() throws Exception {
    String yesterday =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1).toString();
    mvc.perform(
            post(path)
                .cookie(chef).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("2027-01-05", yesterday)))
        .andExpect(status().isBadRequest());
    Long chefId =
        users.findByEmail(jwt.getEmailFromJwtToken(chef.getValue())).orElseThrow().getId();
    Long slotId =
        jdbc.queryForObject(
            "insert into seller_schedules(seller_id,service_date,start_time,end_time,available)"
                + " values (?,?::date,'09:00','12:00',false) returning id",
            Long.class,
            chefId,
            yesterday);
    mvc.perform(get(path + "?from=" + yesterday + "&to=" + yesterday).cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[0].id").value(slotId));
    mvc.perform(
            patch(path + "/" + slotId + "/availability")
                .cookie(chef).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true,\"version\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete(path + "/" + slotId + "?version=0").cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validatesMalformedDatesMissingVersionAndTimes() throws Exception {
    mvc.perform(get(path + "?from=bad&to=2027-01-05").cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path + "?from=2027-01-05").cookie(chef).header("Origin", "http://localhost:5173")).andExpect(status().isBadRequest());
    mvc.perform(post(path).cookie(chef).header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path)
                .cookie(chef).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("12:00", "08:00")))
        .andExpect(status().isBadRequest());
    mvc.perform(delete(path + "/1").cookie(chef).header("Origin", "http://localhost:5173")).andExpect(status().isBadRequest());
    mvc.perform(delete(path + "/1?version=-1").cookie(chef).header("Origin", "http://localhost:5173")).andExpect(status().isBadRequest());
  }
}
