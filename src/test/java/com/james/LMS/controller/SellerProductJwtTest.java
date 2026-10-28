package com.james.LMS.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.james.LMS.entity.User;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.service.CacheService;
import com.james.LMS.service.CloudinaryService;
import com.james.LMS.service.JwtService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
class SellerProductJwtTest {
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @Autowired UserRepository users;
  @Autowired ProductRepository products;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @MockitoBean CacheService cache;
  @MockitoBean CloudinaryService cloudinary;
  final Map<String, String> tokens = new HashMap<>();
  Cookie chef;
  Cookie other;
  Cookie customer;
  Long chefId;

  @BeforeEach
  void setup() {
    chef = account("INSTRUCTOR");
    other = account("INSTRUCTOR");
    customer = account("USER");
    chefId = users.findByEmail(jwt.getEmailFromJwtToken(chef.getValue())).orElseThrow().getId();
    when(cache.hasKey(anyString())).thenAnswer(call -> tokens.containsKey(call.getArgument(0)));
    when(cache.retrieve(anyString())).thenAnswer(call -> tokens.get(call.getArgument(0)));
    when(cloudinary.uploadMenuImage(any()))
        .thenAnswer(
            call ->
                new CloudinaryService.MenuImage(
                    "https://res.cloudinary.com/demo/image/upload/test.png",
                    "gocook/menus/" + UUID.randomUUID()));
  }

  Cookie account(String role) {
    String email = UUID.randomUUID() + "@example.invalid";
    User user =
        users.saveAndFlush(
            User.builder()
                .username("JWT menu test")
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
  void verifiesRealJwtCookieAndOwnershipAcrossCreateReadAndUpdate() throws Exception {
    var request =
        new MockMultipartFile(
            "menu",
            "",
            "application/json",
            SellerProductControllerTest.VALID
                .replace(",\"sellerId\":999", "")
                .getBytes(StandardCharsets.UTF_8));
    var created =
        mvc.perform(multipart("/api/seller/products").file(request).cookie(chef).header("Origin", "http://localhost:5173"))
            .andExpect(status().isCreated())
            .andReturn();
    long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    assertThat(products.findById(id).orElseThrow().getSellerId()).isEqualTo(chefId);
    mvc.perform(get("/api/seller/products/" + id).cookie(chef).header("Origin", "http://localhost:5173")).andExpect(status().isOk());
    mvc.perform(get("/api/seller/products/" + id).cookie(other).header("Origin", "http://localhost:5173")).andExpect(status().isNotFound());
    mvc.perform(get("/api/seller/products/" + id).cookie(customer).header("Origin", "http://localhost:5173"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/seller/products/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/seller/products/" + id).cookie(new Cookie("access-token", "invalid")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void revokedJwtDoesNotPassEvenWhenSignatureIsValid() throws Exception {
    tokens.clear();
    mvc.perform(get("/api/seller/products").cookie(chef).header("Origin", "http://localhost:5173")).andExpect(status().isUnauthorized());
  }

  @Test
  void multipleImagesAndCoverRoundTripThroughTheRealJsonContract() throws Exception {
    String body =
        SellerProductControllerTest.VALID
            .replace(",\"sellerId\":999", "")
            .replace("\"DRAFT\"", "\"PUBLISHED\"")
            .replace("\"imageKeys\":[]", "\"imageKeys\":[\"new:0\",\"new:1\"]")
            .replace("\"coverKey\":\"\"", "\"coverKey\":\"new:0\"");
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    var result =
        mvc.perform(
                multipart("/api/seller/products")
                    .file(
                        new MockMultipartFile(
                            "menu", "", "application/json", body.getBytes(StandardCharsets.UTF_8)))
                    .file(new MockMultipartFile("files", "a.png", "image/png", png))
                    .file(new MockMultipartFile("files", "b.png", "image/png", png))
                    .cookie(chef).header("Origin", "http://localhost:5173"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.images.length()").value(2))
            .andExpect(jsonPath("$.images[0].isCover").value(true))
            .andExpect(jsonPath("$.images[1].isCover").value(false))
            .andReturn();
    var json = mapper.readTree(result.getResponse().getContentAsString());
    long id = json.get("id").asLong();
    String key = "existing:" + json.get("images").get(1).get("id").asLong();
    String update =
        SellerProductControllerTest.VALID
            .replace(",\"sellerId\":999", "")
            .replace("\"imageKeys\":[]", "\"imageKeys\":[\"" + key + "\"]")
            .replace("\"coverKey\":\"\"", "\"coverKey\":\"" + key + "\"")
            .replace(
                "\"name\":\"Menu\"",
                "\"version\":" + json.get("version").asLong() + ",\"name\":\"Changed\"");
    mvc.perform(
            multipart(HttpMethod.PUT, "/api/seller/products/" + id)
                .file(
                    new MockMultipartFile(
                        "menu", "", "application/json", update.getBytes(StandardCharsets.UTF_8)))
                .cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images.length()").value(1))
        .andExpect(jsonPath("$.name").value("Changed"));
    mvc.perform(get("/api/seller/products/" + id).cookie(chef).header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images[0].isCover").value(true));
  }
}
