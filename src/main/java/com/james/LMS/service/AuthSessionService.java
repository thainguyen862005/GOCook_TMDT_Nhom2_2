package com.james.LMS.service;

import com.james.LMS.config.SecurityConfig;
import com.james.LMS.entity.User;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.TokenType;
import com.james.LMS.exception.InvalidTokenException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** One active session per account to remain backward-compatible with the existing Redis keys. */
@Service
@RequiredArgsConstructor
public class AuthSessionService {
  private final CacheService cache;
  private final RedisTemplate<String, Object> redis;
  private final JwtService jwt;
  private final UserService users;

  @Value("${security.cookie.secure:false}") private boolean secure;
  @Value("${security.cookie.same-site:Lax}") private String sameSite;
  @Value("${jwt.accessTokenExpirationTime}") private long accessMillis;
  @Value("${jwt.refreshTokenExpirationTime}") private long refreshMillis;

  public void create(String email, HttpServletResponse response) {
    String access = jwt.generateAccessToken(email);
    String refresh = jwt.generateRefreshToken(email);
    cache.store(accessKey(email), access, seconds(accessMillis), TimeUnit.SECONDS);
    cache.store(refreshKey(email), refresh, seconds(refreshMillis), TimeUnit.SECONDS);
    setCookies(response, access, refresh);
  }

  public void refresh(HttpServletRequest request, HttpServletResponse response) {
    String oldToken = cookie(request, SecurityConfig.COOKIE_REFRESH_TOKEN_NAME);
    jwt.validateTokenForPurpose(oldToken, "refresh");
    String email = jwt.getEmailFromJwtToken(oldToken);
    User user = users.findByEmail(email).orElseThrow(() -> new InvalidTokenException(ErrorCode.JWT_INVALID));
    if (!user.isActive()) throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    String nextRefresh = jwt.generateRefreshToken(email);
    DefaultRedisScript<Long> script = new DefaultRedisScript<>(
        "if redis.call('GET', KEYS[1]) == ARGV[1] then "
        + "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1 else return 0 end",
        Long.class);
    Long updated = redis.execute(script, Collections.singletonList(refreshKey(email)),
        oldToken, nextRefresh, String.valueOf(seconds(refreshMillis)));
    if (!Long.valueOf(1).equals(updated)) throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    String access = jwt.generateAccessToken(email);
    cache.store(accessKey(email), access, seconds(accessMillis), TimeUnit.SECONDS);
    setCookies(response, access, nextRefresh);
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    String refresh = cookie(request, SecurityConfig.COOKIE_REFRESH_TOKEN_NAME);
    if (refresh != null && !refresh.isBlank()) {
      try {
        jwt.validateTokenForPurpose(refresh, "refresh");
        String email = jwt.getEmailFromJwtToken(refresh);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
            + "redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); return 1 else return 0 end",
            Long.class);
        redis.execute(script, java.util.List.of(refreshKey(email), accessKey(email)), refresh);
      } catch (InvalidTokenException ignored) { /* Already expired: still clear the browser. */ }
    }
    clear(response);
  }

  public void consumeResetToken(String key, String token) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>(
        "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
        Long.class);
    Long consumed = redis.execute(script, Collections.singletonList(key), token);
    if (!Long.valueOf(1).equals(consumed)) throw new InvalidTokenException(ErrorCode.JWT_INVALID);
  }

  public void revokeAll(String email) {
    cache.delete(accessKey(email));
    cache.delete(refreshKey(email));
  }

  private void setCookies(HttpServletResponse response, String access, String refresh) {
    cookie(response, SecurityConfig.COOKIE_SECURITY_NAME, access, true, seconds(accessMillis));
    cookie(response, SecurityConfig.COOKIE_REFRESH_TOKEN_NAME, refresh, true, seconds(refreshMillis));
    cookie(response, SecurityConfig.VALIDATE_LOGIN, "true", false, seconds(refreshMillis));
    // Display name and email are deliberately not mirrored in a readable cookie.
    cookie(response, "remember_me", "", false, 0);
  }
  public void clearBrowserCookies(HttpServletResponse response) {
    clear(response);
  }
  private void clear(HttpServletResponse response) {
    cookie(response, SecurityConfig.COOKIE_SECURITY_NAME, "", true, 0);
    cookie(response, SecurityConfig.COOKIE_REFRESH_TOKEN_NAME, "", true, 0);
    cookie(response, SecurityConfig.VALIDATE_LOGIN, "", false, 0);
    cookie(response, "remember_me", "", false, 0);
  }
  private void cookie(HttpServletResponse response, String name, String value, boolean httpOnly, long age) {
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
        .httpOnly(httpOnly).secure(secure).sameSite(sameSite).path("/").maxAge(age).build().toString());
  }
  private static String cookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) return null;
    for (Cookie c : request.getCookies()) if (name.equals(c.getName())) return c.getValue();
    return null;
  }
  private String accessKey(String email) { return String.format(TokenType.ACCESS_TOKEN.getCacheKeyTemplate(), email); }
  private String refreshKey(String email) { return String.format(TokenType.REFRESH_TOKEN.getCacheKeyTemplate(), email); }
  private static int seconds(long milliseconds) { return (int) Math.max(1, (milliseconds + 999) / 1000); }
}
