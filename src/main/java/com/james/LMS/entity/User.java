package com.james.LMS.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class User extends BaseEntity {

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "password", nullable = false)
  private String password;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "default_address", length = 500)
  private String defaultAddress;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column(name = "password_login_enabled", nullable = false)
  @Builder.Default
  private boolean passwordLoginEnabled = true;

  @Column(name = "email_verified", nullable = false)
  @Builder.Default
  private boolean emailVerified = false;

  @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @Builder.Default
  private List<Role> roles = new ArrayList<>();

  public void changePassword(String newPasswordEncoded) {
    this.password = newPasswordEncoded;
  }

  public void verifyEmail() {
    this.emailVerified = true;
  }

  public void enablePasswordLogin() { this.passwordLoginEnabled = true; }

  public void addRole(Role role) {
    this.roles.add(role);
  }

  public void addAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public void changePhone(String phone) { this.phone = phone; }

  public void changeDefaultAddress(String defaultAddress) { this.defaultAddress = defaultAddress; }

  public void changeUsername(String username) {
    this.username = username;
  }
}
