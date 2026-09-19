package dev.grindtrack.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One browser on one device that asked to be told things.
 *
 * <p>The endpoint is the push service's address for that browser and is the row's identity: a
 * browser that subscribes twice replaces its keys rather than doubling its notifications. The two
 * keys are the browser's, generated when it subscribed; every payload is encrypted to them, which
 * is what lets a sentence of the brief transit Apple's servers unread.
 *
 * <p>The row belongs to the account that subscribed it. The scheduled pushes go to the owner's
 * devices and nobody else's; a partner's phone is told only what is addressed to them.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, unique = true)
  private String endpoint;

  @Column(nullable = false)
  private String p256dh;

  @Column(nullable = false)
  private String auth;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_sent_at")
  private OffsetDateTime lastSentAt;

  protected PushSubscription() {}

  public PushSubscription(
      Long userId, String endpoint, String p256dh, String auth, String userAgent) {
    this.userId = userId;
    this.endpoint = endpoint;
    this.createdAt = OffsetDateTime.now();
    replaceKeys(p256dh, auth, userAgent);
  }

  /** A re-subscription from the same browser: new keys, same row. */
  public void replaceKeys(String p256dh, String auth, String userAgent) {
    this.p256dh = p256dh;
    this.auth = auth;
    this.userAgent = userAgent;
  }

  /**
   * The same browser, signed in as someone else now. The endpoint is the browser's, not the
   * account's, so the row follows whoever subscribed last rather than telling the new person the
   * old person's things.
   */
  public void belongsTo(Long userId) {
    this.userId = userId;
  }

  public void markSent() {
    this.lastSentAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getP256dh() {
    return p256dh;
  }

  public String getAuth() {
    return auth;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getLastSentAt() {
    return lastSentAt;
  }
}
