package com.james.LMS.config;

import com.james.LMS.interceptor.AuthTokenProviderInterceptor;
import com.james.LMS.interceptor.UserDetailsAuthenticationProviderInterceptor;
import com.james.LMS.service.CacheService;
import com.james.LMS.service.JwtService;
import com.james.LMS.service.RoleService;
import com.james.LMS.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.servlet.DispatcherType;
@Slf4j
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {
  private final UserService userService;
  private final JwtService jwtService;
  private final CacheService cacheService;
  private final RoleService roleService;

  public static final String SECURITY_REQUIREMENT = "Bearer Authentication";
  public static final String COOKIE_SECURITY_NAME = "access-token";
  public static final String COOKIE_REFRESH_TOKEN_NAME = "refresh-token";
  public static final String VALIDATE_LOGIN = "validate-login";

  private static final String[] WHITE_LISTS = {
      "/",
      "/login",
      "/register",
      "/logout",
      "/forgot-password",
      "/reset-password",
      "/verify-email",
      "/chefs",
      "/chef-detail",
      "/chef-an-nhien",
      "/menus",
      "/menu-detail",
      "/menu-vegetarian",
      "/offers",
      "/help",
      "/request",
      "/pages",
      "/quotes",
      "/review",
      "/matching",
      "/become-chef",
      "/chat",
      "/app/**",
      "/assets/**",
      "/favicon.ico",
      "/swagger-ui/**",
      "/v3/api-docs/**",
      "/api/v1/users/sign-up",
      "/api/v1/users/verify-email",
      "/api/v1/users/resend-verification",
      "/api/v1/users/request-email-verification",
      "/api/v1/users/refresh-token",
      "/api/v1/users/forgot-password",
      "/api/v1/users/verify-otp",
      "/api/v1/users/reset-password",
      "/api/v2/users/login",
      "/api/payments/vnpay-ipn",
      "/actuator/**",
      "/api/v1/users/demo",
      "/api/products/**",
      "/api/public/site-settings",
      "/api/public/chefs",
      "/api/public/chefs/**",
      "/api/public/offers",
      "/api/public/faqs"
  };
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
      throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  public UserDetailsAuthenticationProviderInterceptor
      userDetailsAuthenticationProviderInterceptor() {
    return new UserDetailsAuthenticationProviderInterceptor(
        this.userService, this.passwordEncoder(), this.roleService);
  }

  @Bean
  public AuthTokenProviderInterceptor authTokenProviderInterceptor() {
    return new AuthTokenProviderInterceptor(
        this.userService, this.cacheService, this.jwtService, this.roleService);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(
                    session ->
                            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(
                    request ->
                            request
                                    .dispatcherTypeMatchers(
                                            DispatcherType.FORWARD,
                                            DispatcherType.ERROR)
                                    .permitAll()
                                    .requestMatchers(WHITE_LISTS)
                                    .permitAll()
                                    .anyRequest()
                                    .authenticated());

    http.authenticationProvider(this.userDetailsAuthenticationProviderInterceptor());

    http.addFilterBefore(
            authTokenProviderInterceptor(),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
