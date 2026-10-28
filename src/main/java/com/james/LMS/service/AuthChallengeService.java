package com.james.LMS.service;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PermissionDeniedException;
import com.james.LMS.util.OTPGeneratorUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Redis-backed OTP challenges. Never stores raw OTP values; Redis is required, not optional. */
@Service
@RequiredArgsConstructor
public class AuthChallengeService {
  private static final int OTP_MINUTES = 10;
  private static final int MAX_OTP_ATTEMPTS = 5;
  private static final int MAX_LOGIN_FAILURES = 8;
  private static final SecureRandom NONCE_RANDOM = new SecureRandom();
  private final RedisTemplate<String, Object> redis;

  @Value("${jwt.secretKey}") private String secret;

  public String issue(String purpose, String email) {
    String otp = OTPGeneratorUtil.generaRandomCode();
    String key = otpKey(purpose, email);
    redis.opsForValue().set(key, digest(purpose, email, otp), OTP_MINUTES, TimeUnit.MINUTES);
    redis.delete(attemptKey(purpose, email));
    return otp;
  }

  /** Single use, enforced with a Redis Lua compare-and-delete (atomic across requests). */
  public void consume(String purpose, String email, String otp) {
    String attempts = attemptKey(purpose, email);
    Long count = redis.opsForValue().increment(attempts);
    if (count == null) throw new IllegalStateException("Redis unavailable");
    if (count == 1L) redis.expire(attempts, OTP_MINUTES, TimeUnit.MINUTES);
    if (count > MAX_OTP_ATTEMPTS) {
      redis.delete(otpKey(purpose, email));
      throw new PermissionDeniedException(ErrorCode.NOT_MATCHED_OTP);
    }
    String expected = digest(purpose, email, otp);
    DefaultRedisScript<Long> script = new DefaultRedisScript<>(
        "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
        Long.class);
    Long result = redis.execute(script, Collections.singletonList(otpKey(purpose, email)), expected);
    if (result == null || result != 1L) {
      throw new PermissionDeniedException(ErrorCode.NOT_MATCHED_OTP);
    }
    redis.delete(attempts);
  }

  /** Atomically reserves the email-send cooldown; works for unknown accounts too. */
  public boolean reserveSend(String purpose, String email) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
        "auth:send:" + purpose + ":" + safeKey(email), "1", 60, TimeUnit.SECONDS));
  }

  public void requireLoginAllowed(String email) {
    Object count = redis.opsForValue().get(loginKey(email));
    if (count != null && Long.parseLong(String.valueOf(count)) >= MAX_LOGIN_FAILURES) {
      throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
    }
  }

  public void failedLogin(String email) {
    String key = loginKey(email);
    Long count = redis.opsForValue().increment(key);
    if (count == null) throw new IllegalStateException("Redis unavailable");
    if (count == 1L) redis.expire(key, 15, TimeUnit.MINUTES);
  }

  public void requireIpRate(String action, String ip, int maximum, int windowMinutes) {
    String key = "auth:ip:" + action + ":" + safeKey(ip == null ? "unknown" : ip);
    Long count = redis.opsForValue().increment(key);
    if (count == null) throw new IllegalStateException("Redis unavailable");
    if (count == 1L) redis.expire(key, windowMinutes, TimeUnit.MINUTES);
    if (count > maximum) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Vui lòng thử lại sau ít phút");
    }
  }

  public String issueGoogleNonce() {
    byte[] bytes = new byte[32];
    NONCE_RANDOM.nextBytes(bytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redis.opsForValue().set("auth:google:nonce:" + nonce, "1", 5, TimeUnit.MINUTES);
    return nonce;
  }

  public void consumeGoogleNonce(String nonce) {
    if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")) {
      throw new org.springframework.security.authentication.BadCredentialsException("Invalid Google nonce");
    }
    // GETDEL makes replay of the same Google credential in our app impossible.
    Object removed = redis.opsForValue().getAndDelete("auth:google:nonce:" + nonce);
    if (!"1".equals(String.valueOf(removed))) {
      throw new org.springframework.security.authentication.BadCredentialsException("Invalid Google nonce");
    }
  }

  public void clearLoginFailures(String email) {
    redis.delete(loginKey(email));
  }

  public void invalidateChallenge(String purpose, String email) {
    redis.delete(otpKey(purpose, email));
  }

  private String otpKey(String purpose, String email) {
    return "auth:otp:" + purpose + ":" + safeKey(email);
  }
  private String attemptKey(String purpose, String email) {
    return "auth:otp-attempt:" + purpose + ":" + safeKey(email);
  }
  private String loginKey(String email) { return "auth:login:" + safeKey(email); }

  private String safeKey(String email) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(email.trim().toLowerCase(Locale.ROOT)
          .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) { throw new IllegalStateException(error); }
  }

  private String digest(String purpose, String email, String otp) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(
          (purpose + ":" + email.toLowerCase(Locale.ROOT) + ":" + otp)
              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) { throw new IllegalStateException(error); }
  }
}
