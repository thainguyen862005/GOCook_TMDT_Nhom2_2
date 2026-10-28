package com.james.LMS.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Guards against pre-security JWT filtering rejecting public Google login endpoints. */
class PublicEndpointsValidatorUtilTest {

  @Test
  void publicGoogleLoginEndpointsDoNotRequireAccessToken() {
    assertTrue(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v2/users/google/nonce"));
    assertTrue(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v2/users/google/sign-in"));
  }

  @Test
  void accountLinkAndPasswordSetupMustRemainProtected() {
    assertFalse(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v2/users/google/link"));
    assertFalse(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v2/users/google/set-password"));
    assertFalse(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v2/users/google/admin"));
  }

  @Test
  void existingPublicAuthEndpointsArePreserved() {
    assertTrue(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v2/users/login"));
    assertTrue(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v1/users/sign-up"));
    assertTrue(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/v1/users/refresh-token"));
    assertTrue(PublicEndpointsValidatorUtil.isPublicEndpoint("/api/public/site-settings"));
  }
}
