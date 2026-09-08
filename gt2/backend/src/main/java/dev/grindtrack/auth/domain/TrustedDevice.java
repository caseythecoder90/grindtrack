package dev.grindtrack.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A browser that has already proved it holds the second factor.
 *
 * <p>Only a SHA-256 hash of the token is stored, the same rule {@link RefreshToken} follows: a
 * database leak must not hand out anything usable. This one is worth even more care, because it
 * does not expire in minutes and it weakens a second factor rather than a session.
 *
 * <p>It authenticates nothing on its own. Presenting it lets a correct password in without an
 * authenticator code; presenting it with a wrong password gets exactly as far as a wrong password
 * always did.
 */
@Entity
@Table(name = "trusted_devices")
public class TrustedDevice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  @Column(nullable = false)
  private boolean revoked;

  protected TrustedDevice() {}

  public TrustedDevice(Long userId, String tokenHash, OffsetDateTime expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revoked = false;
  }

  public Long getUserId() {
    return userId;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void revoke() {
    this.revoked = true;
  }

  /** Records a sign-in and slides the expiry, so a device in regular use stays trusted. */
  public void used(OffsetDateTime at, OffsetDateTime newExpiry) {
    this.lastUsedAt = at;
    this.expiresAt = newExpiry;
  }

  public boolean isUsable(OffsetDateTime now) {
    return !revoked && expiresAt.isAfter(now);
  }
}
