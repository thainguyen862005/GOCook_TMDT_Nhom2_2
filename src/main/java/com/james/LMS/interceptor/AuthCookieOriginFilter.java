package com.james.LMS.interceptor;

import com.james.LMS.config.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** For browser cookie-authenticated unsafe methods, deny requests not from a trusted origin. */
@Component
public class AuthCookieOriginFilter extends OncePerRequestFilter {
  @Value("${security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:8083}")
  private String origins;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String method = request.getMethod();
    if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
        || "TRACE".equals(method) || (!hasSessionCookie(request) && !isLoginPath(request))) {
      chain.doFilter(request, response); return;
    }
    Set<String> allowed = Arrays.stream(origins.split(",")).map(String::trim)
        .filter(s -> !s.isBlank()).collect(Collectors.toSet());
    String source = request.getHeader("Origin");
    if (source == null || source.isBlank()) {
      String referer = request.getHeader("Referer");
      if (referer != null) {
        try {
          URI uri = URI.create(referer);
          source = uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException ignored) { source = null; }
      }
    }
    if (source == null || !allowed.contains(source)
        || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Untrusted request origin");
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isLoginPath(HttpServletRequest request) {
    return "/api/v2/users/login".equals(request.getRequestURI())
        || "/api/v2/users/google/sign-in".equals(request.getRequestURI());
  }

  private static boolean hasSessionCookie(HttpServletRequest request) {
    if (request.getCookies() == null) return false;
    for (Cookie c : request.getCookies()) {
      if (SecurityConfig.COOKIE_SECURITY_NAME.equals(c.getName())
          || SecurityConfig.COOKIE_REFRESH_TOKEN_NAME.equals(c.getName())) return true;
    }
    return false;
  }
}
