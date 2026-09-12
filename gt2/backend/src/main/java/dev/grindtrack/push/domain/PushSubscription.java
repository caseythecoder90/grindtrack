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
 * <p>One account, so no owner column. The day a second user exists this table needs one, like every
 * other table in the app.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

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

  public PushSubscription(String endpoint, String p256dh, String auth, String userAgent) {
    this.endpoint = endpoint;
    this.userAgent = userAgent;
    this.createdAt = OffsetDateTime.now();
    replaceKeys(p256dh, auth, userAgent);
  }

  /** A re-subscription from the same browser: new keys, same row. */
  public void replaceKeys(String p256dh, String auth, String userAgent) {
    this.p256dh = p256dh;
    this.auth = auth;
    this.userAgent = userAgent;
  }

  public void markSent() {
    this.lastSentAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
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
