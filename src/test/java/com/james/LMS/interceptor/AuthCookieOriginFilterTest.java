package com.james.LMS.interceptor;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class AuthCookieOriginFilterTest {
  private AuthCookieOriginFilter filter;
  @BeforeEach void setup() {
    filter = new AuthCookieOriginFilter();
    ReflectionTestUtils.setField(filter, "origins", "http://localhost:5173,https://gocook.example");
  }
  private MockHttpServletRequest request(String path, String origin) {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
    req.setCookies(new Cookie("access-token", "test"));
    if (origin != null) req.addHeader("Origin", origin);
    return req;
  }
  @Test void crossSiteCookieWritesAreRejected() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request("/api/v1/users/profile", "https://attacker.example"), response, new MockFilterChain());
    assertEquals(403, response.getStatus());
  }
  @Test void missingOriginIsRejected() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request("/api/v1/users/profile", null), response, new MockFilterChain());
    assertEquals(403, response.getStatus());
  }
  @Test void trustedOriginPasses() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request("/api/v1/users/profile", "http://localhost:5173"), response, new MockFilterChain());
    assertEquals(200, response.getStatus());
  }
  @Test void loginCsrfIsRejectedWithoutCookie() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v2/users/login");
    req.addHeader("Origin", "https://attacker.example");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(req, response, new MockFilterChain());
    assertEquals(403, response.getStatus());
  }
}
