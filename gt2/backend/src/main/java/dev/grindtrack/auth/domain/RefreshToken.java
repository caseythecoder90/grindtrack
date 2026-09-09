package dev.grindtrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Server-side record of an issued refresh token. Only a SHA-256 hash is stored: a database leak
 * must not hand out usable tokens. Rotation marks the old row rotated and inserts its successor
 * into the same family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /**
   * The login this token descends from. A login starts a family; every rotation issues the
   * successor into the same one. Reuse detection revokes a family and never a user, so a stale
   * cookie on one device can only ever end the session it belonged to -- never the sessions other
   * devices are in the middle of using. See {@code AuthService#renew}.
   */
  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

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
   * When this token was exchanged for a successor, or null if it is live or was revoked outright
   * (a logout, or its family being revoked). Rotation and revocation both set {@code revoked}; only
   * rotation sets this, and only a rotated token can be evidence of reuse -- see {@code
   * AuthService#renew}.
   */
  @Column(name = "rotated_at")
  private OffsetDateTime rotatedAt;

  protected RefreshToken() {}

  /** The first token of a new family, issued now. */
  public RefreshToken(Long userId, String tokenHash, OffsetDateTime expiresAt) {
    this(userId, UUID.randomUUID(), tokenHash, OffsetDateTime.now(), expiresAt);
  }

  public RefreshToken(
      Long userId,
      UUID familyId,
      String tokenHash,
      OffsetDateTime issuedAt,
      OffsetDateTime expiresAt) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.createdAt = issuedAt;
    this.expiresAt = expiresAt;
    this.revoked = false;
  }

  public Long getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
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

  /** True once a successor has been issued for this token. */
  public boolean isRotated() {
    return rotatedAt != null;
  }

  /** Ends the token for good: an explicit logout, or its family being revoked. */
  public void revoke() {
    this.revoked = true;
  }

  /**
   * Ends the token because a successor was issued for it.
   *
   * <p>Separate from {@link #revoke()} on purpose. Both leave the token unusable, but only this one
   * records an instant, and only a token with that instant can be evidence of reuse: a successor
   * exists that a thief could be racing the owner for. A token that was simply revoked has no
   * successor, so presenting it proves nothing and protects nothing.
   */
  public void markRotated(OffsetDateTime at) {
    this.revoked = true;
    this.rotatedAt = at;
  }
}
