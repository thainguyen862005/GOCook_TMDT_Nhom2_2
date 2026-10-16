package com.james.LMS.util;

import java.util.List;

public class PublicEndpointsValidatorUtil {

  private static final List<String> PUBLIC_ENDPOINTS =
          List.of(
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

                  "/actuator/health",
                  "/actuator/beans",
                  "/api/v1/users/demo",
                  "/api/v1/users/login",
                  "/api/v1/users/sign-up",
                  "/api/v1/users/verify-email",
                  "/api/v1/users/resend-verification",
                  "/api/v1/users/request-email-verification",
                  "/api/v1/users/forgot-password",
                  "/api/v1/users/refresh-token",
                  "/api/v1/users/verify-otp",
                  "/api/v1/users/reset-password",
                  "/api/v2/users/login",
                  "/api/payments/vnpay-ipn");

  private static final List<String> PUBLIC_PREFIXES =
          List.of(
                  "/css/",
                  "/assets/",
                  "/js/",
                  "/swagger-ui/",
                  "/v3/api-docs/",
                  "/actuator/");

  public static boolean isSwaggerUrl(String path) {
    return path.startsWith("/swagger-ui/")
            || path.startsWith("/v3/api-docs/");
  }

  public static boolean isPublicEndpoint(String path) {
    return PUBLIC_ENDPOINTS.contains(path)
            || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
  }
}