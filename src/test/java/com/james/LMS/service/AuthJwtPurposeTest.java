package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;

import com.james.LMS.exception.InvalidTokenException;
import com.james.LMS.service.impl.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthJwtPurposeTest {
  private JwtServiceImpl jwt;
  @BeforeEach void setup() {
    jwt = new JwtServiceImpl();
    ReflectionTestUtils.setField(jwt, "SECRET_KEY", java.util.Base64.getEncoder().encodeToString(new byte[64]));
    ReflectionTestUtils.setField(jwt, "ACCESS_TOKEN_EXPIRATION_TIME", "900000");
    ReflectionTestUtils.setField(jwt, "REFRESH_TOKEN_EXPIRATION_TIME", "1209600000");
    ReflectionTestUtils.setField(jwt, "RESET_PASSWORD_TOKEN_EXPIRATION_TIME", "900000");
  }
  @Test void tokenTypesCannotBeSubstituted() {
    String access = jwt.generateAccessToken("demo@gocook.test");
    String refresh = jwt.generateRefreshToken("demo@gocook.test");
    String reset = jwt.generateResetPasswordToken("demo@gocook.test");
    assertTrue(jwt.validateTokenForPurpose(access, "access"));
    assertTrue(jwt.validateTokenForPurpose(refresh, "refresh"));
    assertTrue(jwt.validateTokenForPurpose(reset, "reset"));
    assertThrows(InvalidTokenException.class, () -> jwt.validateTokenForPurpose(refresh, "access"));
    assertThrows(InvalidTokenException.class, () -> jwt.validateTokenForPurpose(reset, "access"));
    assertThrows(InvalidTokenException.class, () -> jwt.validateTokenForPurpose(access, "reset"));
    assertThrows(InvalidTokenException.class, () -> jwt.validateTokenForPurpose("nonsense", "access"));
  }
  @Test void eachSessionTokenHasUniqueId() {
    assertNotEquals(jwt.generateRefreshToken("demo@gocook.test"),
        jwt.generateRefreshToken("demo@gocook.test"));
  }
}
