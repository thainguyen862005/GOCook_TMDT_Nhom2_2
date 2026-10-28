package com.james.LMS.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Stable provider subject, never the email, is the federated account identifier. */
@Entity
@Table(name = "user_auth_identities", uniqueConstraints =
    @UniqueConstraint(name = "uq_user_auth_provider_subject", columnNames = {"provider", "subject"}))
@Getter
@NoArgsConstructor
public class UserAuthIdentity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, length = 24)
  private String provider;
  @Column(nullable = false, length = 255)
  private String subject;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  public UserAuthIdentity(String provider, String subject, User user) {
    this.provider = provider;
    this.subject = subject;
    this.user = user;
  }
}
