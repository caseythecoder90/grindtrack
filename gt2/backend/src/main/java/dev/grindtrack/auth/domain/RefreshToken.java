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

  /**
   * When this token was first issued. Rotation is scheduled off it: a session is renewed on every
   * use, but the token itself is only replaced once it is older than the rotation interval.
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private boolean revoked;

  /**
   * When this token was exchanged for a successor, or null if it is live or was revoked by an
   * explicit logout. Rotation and logout both set {@code revoked}; only rotation sets this, and
   * only rotation earns the grace window in {@link
   * dev.grindtrack.auth.service.AuthService#rotate(String)}.
   */
  @Column(name = "rotated_at")
  private OffsetDateTime rotatedAt;

  protected RefreshToken() {}

  public RefreshToken(Long userId, String tokenHash, OffsetDateTime expiresAt) {
    this(userId, tokenHash, OffsetDateTime.now(), expiresAt);
  }

  public RefreshToken(
      Long userId, String tokenHash, OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.createdAt = issuedAt;
    this.expiresAt = expiresAt;
    this.revoked = false;
  }

  public Long getUserId() {
    return userId;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Push the expiry out. This is what keeps a session alive: using it renews it, so an app you open
   * every day never expires, and one you abandon for the whole window does.
   */
  public void renewUntil(OffsetDateTime newExpiry) {
    this.expiresAt = newExpiry;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public OffsetDateTime getRotatedAt() {
    return rotatedAt;
  }

  /** Ends the token for good: an explicit logout, or the reuse cascade. No grace follows. */
  public void revoke() {
    this.revoked = true;
  }

  /**
   * Ends the token because a successor was issued for it.
   *
   * <p>Separate from {@link #revoke()} on purpose. Both leave the token unusable, but only this one
   * records an instant, and only tokens with that instant are eligible for the grace window.
   * Presenting a logged-out token is not a race and must not be treated as one.
   */
  public void markRotated(OffsetDateTime at) {
    this.revoked = true;
    this.rotatedAt = at;
  }
}
