package com.james.LMS.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.exception.PermissionDeniedException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class AuthChallengeServiceTest {
  @Mock RedisTemplate<String, Object> redis;
  @Mock ValueOperations<String, Object> values;
  private AuthChallengeService service;
  @BeforeEach void setup() {
    MockitoAnnotations.openMocks(this);
    when(redis.opsForValue()).thenReturn(values);
    service = new AuthChallengeService(redis);
    ReflectionTestUtils.setField(service, "secret", "test-secret-long-enough-for-hmac-1234567890");
  }
  @Test void otpIsStoredAsDigestNotSixDigitPlaintext() {
    String otp = service.issue("verify", "user@example.com");
    assertEquals(6, otp.length());
    assertTrue(otp.matches("[0-9]{6}"));
    verify(values).set(startsWith("auth:otp:verify:"), argThat(value ->
        value instanceof String digest && digest.length() == 64 && !digest.equals(otp)),
        eq(10L), eq(TimeUnit.MINUTES));
  }
  @Test void incorrectAttemptsEventuallyLockChallenge() {
    when(values.increment(anyString())).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);
    for (int i=0; i<6; i++) {
      assertThrows(PermissionDeniedException.class,
          () -> service.consume("verify", "user@example.com", "000000"));
    }
    verify(redis, atLeastOnce()).delete(startsWith("auth:otp:verify:"));
  }
  @Test void unknownAccountEmailCannotResendRepeatedlyWithinCooldown() {
    when(values.setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
        .thenReturn(true, false);
    assertTrue(service.reserveSend("verify", "nobody@example.com"));
    assertFalse(service.reserveSend("verify", "nobody@example.com"));
  }
}
