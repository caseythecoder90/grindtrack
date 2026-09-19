package dev.grindtrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "totp_secret", nullable = false)
  private String totpSecret;

  /**
   * Fixed at creation. An account does not change kind; a different kind is a different account.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private Role role;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected User() {}

  public User(String username, String passwordHash, String totpSecret, Role role) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.totpSecret = totpSecret;
    this.role = role;
  }

  /** A new password, already hashed. The owner resetting a partner's; there is no other path. */
  public void replacePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getTotpSecret() {
    return totpSecret;
  }

  public Role getRole() {
    return role;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
