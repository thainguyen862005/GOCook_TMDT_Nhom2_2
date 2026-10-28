package com.james.LMS.interceptor;

import com.james.LMS.config.SecurityConfig;
import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.Role;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.TokenType;
import com.james.LMS.exception.EntityNotFoundException;
import com.james.LMS.exception.InvalidTokenException;
import com.james.LMS.service.CacheService;
import com.james.LMS.service.JwtService;
import com.james.LMS.service.RoleService;
import com.james.LMS.service.UserService;
import com.james.LMS.util.PublicEndpointsValidatorUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class AuthTokenProviderInterceptor extends OncePerRequestFilter {

  private final UserService userService;
  private final CacheService cacheService;
  private final JwtService jwtService;
  private final RoleService roleService;

  public static final String AUTHORIZATION = "Authorization";
  private static final int START_OF_TOKEN = 7;

  //  public static final List<String> SWAGGER_URLS = List.of("/swagger-ui/", "/v3/api-docs");
  //  public static final List<String> PUBLIC_ENDPOINTS =
  //      List.of(
  //          "/api/v1/users/demo",
  //          "/actuator/health",
  //          "/actuator/beans",
  //          "/api/v1/users/login",
  //          "/api/v1/users/sign-up",
  //          "/api/v1/users/forgot-password",
  //          "/api/v1/users/refresh-token",
  //          "/api/v1/users/verify-otp");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();

    if (PublicEndpointsValidatorUtil.isSwaggerUrl(path)
        || PublicEndpointsValidatorUtil.isPublicEndpoint(path)) {
      filterChain.doFilter(request, response);
      return;
    }
    String token = this.getJwtTokenFromCookie(request);

    try {
      boolean isValidToken = jwtService.validateTokenForPurpose(token, "access");
      if (!isValidToken) throw new InvalidTokenException(ErrorCode.JWT_INVALID);

      String email = jwtService.getEmailFromJwtToken(token);
      if (!validateTokenFromCache(email, token, path))
        throw new InvalidTokenException(ErrorCode.JWT_INVALID);

      SecurityUserDetails securityUserDetails = this.buildUserDetailsByEmail(email);

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              securityUserDetails, null, securityUserDetails.getAuthorities());

      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);

    } catch (InvalidTokenException | EntityNotFoundException invalidTokenException) {
      log.info("request unauthorized");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  private SecurityUserDetails buildUserDetailsByEmail(String email) {
    var user =
        userService
            .findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    if (!user.isActive()) throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    List<Role> roles = this.roleService.findAllByUserId(user.getId());
    List<GrantedAuthority> authorityList =
        roles.stream()
            .map(role -> new SimpleGrantedAuthority(role.getRoleName().getContent()))
            .collect(Collectors.toList());

    return SecurityUserDetails.build(user, authorityList);
  }

  private Boolean validateTokenFromCache(String email, String token, String path) {
    if (token == null) return false;

    var accessTokenCacheKey = String.format(TokenType.ACCESS_TOKEN.getCacheKeyTemplate(), email);

    return cacheService.hasKey(accessTokenCacheKey)
        && cacheService.retrieve(accessTokenCacheKey).equals(token);
  }

  @Deprecated
  public String getJwtTokenFromHeader(HttpServletRequest request) {
    String headerAuth = request.getHeader(AUTHORIZATION);
    if (headerAuth != null) {
      return headerAuth.substring(START_OF_TOKEN);
    }
    return null;
  }

  public String getJwtTokenFromCookie(HttpServletRequest request) {
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if (SecurityConfig.COOKIE_SECURITY_NAME.equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }
}
