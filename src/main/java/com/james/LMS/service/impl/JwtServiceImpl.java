package com.james.LMS.service.impl;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.InvalidTokenException;
import com.james.LMS.service.JwtService;
import io.jsonwebtoken.*;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {
  @Value("${jwt.secretKey}")
  private String SECRET_KEY;

  @Value("${jwt.accessTokenExpirationTime}")
  private String ACCESS_TOKEN_EXPIRATION_TIME;

  @Value("${jwt.refreshTokenExpirationTime}")
  private String REFRESH_TOKEN_EXPIRATION_TIME;

  @Value("${jwt.resetPasswordTokenExpirationTime}")
  private String RESET_PASSWORD_TOKEN_EXPIRATION_TIME;

  @Override
  public String generateAccessToken(String email) {
    return Jwts.builder()
        .setSubject(email)
        .claim("token_use", "access")
        .setId(UUID.randomUUID().toString())
        .setIssuedAt(new Date())
        .setExpiration(
            new Date(System.currentTimeMillis() + Long.parseLong(ACCESS_TOKEN_EXPIRATION_TIME)))
        .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
        .compact();
  }

  @Override
  public String generateRefreshToken(String email) {
    return Jwts.builder()
        .setSubject(email)
        .claim("token_use", "refresh")
        .setId(UUID.randomUUID().toString())
        .setIssuedAt(new Date())
        .setExpiration(
            new Date(System.currentTimeMillis() + Long.parseLong(REFRESH_TOKEN_EXPIRATION_TIME)))
        .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
        .compact();
  }

  @Override
  public String generateResetPasswordToken(String email) {
    return Jwts.builder()
        .setSubject(email)
        .claim("token_use", "reset")
        .setId(UUID.randomUUID().toString())
        .setIssuedAt(new Date())
        .setExpiration(
            new Date(
                System.currentTimeMillis() + Long.parseLong(RESET_PASSWORD_TOKEN_EXPIRATION_TIME)))
        .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
        .compact();
  }

  private Claims parseClaims(String token) {
    if (token == null || token.isBlank()) throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    try {
      return Jwts.parserBuilder().setSigningKey(SECRET_KEY).build()
          .parseClaimsJws(token).getBody();
    } catch (JwtException | IllegalArgumentException error) {
      throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    }
  }

  @Override
  public Boolean validateToken(String token) {
    parseClaims(token);
    return true;
  }

  @Override
  public Boolean validateTokenForPurpose(String token, String purpose) {
    Claims claims = parseClaims(token);
    if (!purpose.equals(claims.get("token_use", String.class))) {
      throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    }
    return true;
  }

  @Override
  public String getEmailFromJwtToken(String token) {
    String email = parseClaims(token).getSubject();
    if (email == null || email.isBlank()) throw new InvalidTokenException(ErrorCode.JWT_INVALID);
    return email;
  }
}
