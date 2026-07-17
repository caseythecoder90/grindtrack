package dev.grindtrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected User() {}

  public User(String username, String passwordHash, String totpSecret) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.totpSecret = totpSecret;
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
}
