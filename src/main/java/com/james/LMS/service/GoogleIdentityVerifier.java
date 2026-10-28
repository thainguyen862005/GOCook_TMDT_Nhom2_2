package com.james.LMS.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/** Verifies Google signatures and claims using Google's rotating public keys. */
@Service
public class GoogleIdentityVerifier {
  private final String clientId;
  private final GoogleIdTokenVerifier verifier;

  public record Identity(String subject, String email, String displayName,
                         boolean authoritativeEmail, String nonce) {}

  public GoogleIdentityVerifier(@Value("${auth.google.client-id:}") String clientId) {
    this.clientId = clientId.trim();
    this.verifier = this.clientId.isBlank() ? null : new GoogleIdTokenVerifier.Builder(
        new NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(Collections.singletonList(this.clientId)).build();
  }

  public Identity verify(String rawToken) {
    if (verifier == null) throw new IllegalStateException("Google sign-in is not configured");
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 8192)
      throw new BadCredentialsException("Invalid Google credential");
    try {
      GoogleIdToken token = verifier.verify(rawToken);
      if (token == null) throw new BadCredentialsException("Invalid Google credential");
      GoogleIdToken.Payload p = token.getPayload();
      String subject = p.getSubject();
      String email = p.getEmail();
      if (subject == null || subject.isBlank() || email == null || email.isBlank()
          || !Boolean.TRUE.equals(p.getEmailVerified()))
        throw new BadCredentialsException("Invalid Google identity");
      email = email.trim().toLowerCase(Locale.ROOT);
      // Google is authoritative for Gmail and verified Workspace addresses;
      // it may not be authoritative for third-party email accounts.
      boolean authoritative = email.endsWith("@gmail.com") ||
          (p.getHostedDomain() != null && !p.getHostedDomain().isBlank());
      String name = (String) p.get("name");
      String nonce = (String) p.get("nonce");
      return new Identity(subject, email, name, authoritative, nonce);
    } catch (IOException | GeneralSecurityException error) {
      throw new BadCredentialsException("Google identity could not be verified", error);
    }
  }
}
