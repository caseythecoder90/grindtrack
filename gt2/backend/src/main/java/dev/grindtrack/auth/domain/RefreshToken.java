package dev.grindtrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Server-side record of an issued refresh token. Only a SHA-256 hash is stored: a database leak
 * must not hand out usable tokens. Rotation marks the old row revoked and inserts a new one.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(nullable = false)
  private boolean revoked;

  protected RefreshToken() {}

  public RefreshToken(Long userId, String tokenHash, OffsetDateTime expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revoked = false;
  }

  public Long getUserId() {
    return userId;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public void revoke() {
    this.revoked = true;
  }
}
