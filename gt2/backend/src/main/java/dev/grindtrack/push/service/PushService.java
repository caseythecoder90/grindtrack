package dev.grindtrack.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.grindtrack.config.PushProperties;
import dev.grindtrack.push.domain.PushSubscription;
import dev.grindtrack.push.domain.PushSubscriptionRepository;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscriptions in, notifications out.
 *
 * <p>Off is a state: with no VAPID pair the status says so, subscribing is a 503 with a sentence,
 * and {@link #send} quietly sends nothing, so a scheduler can call it without checking first. A
 * subscription that the push service reports gone (404 or 410) is deleted on the spot; anything
 * else that goes wrong is logged and the row kept. No retries — a missed six o'clock push is a
 * missed nudge, and the brief is still on the today tab.
 *
 * <p>Sending is deliberately not transactional: it is network I/O of up to ten seconds per device,
 * and a pooled connection held across that would be one of ten doing nothing. The repository calls
 * around it are each their own short transaction.
 */
@Service
public class PushService {

  private static final Logger log = LoggerFactory.getLogger(PushService.class);
  private static final Base64.Decoder B64D = Base64.getUrlDecoder();

  private final PushProperties props;
  private final PushSubscriptionRepository subscriptions;
  private final PushTransport transport;
  private final ObjectMapper mapper;

  /** Null when off. Built once: parsing the keys is the startup check that they are keys. */
  private final Vapid vapid;

  public PushService(
      PushProperties props,
      PushSubscriptionRepository subscriptions,
      PushTransport transport,
      ObjectMapper mapper) {
    this.props = props;
    this.subscriptions = subscriptions;
    this.transport = transport;
    this.mapper = mapper;
    this.vapid = props.configured() ? Vapid.from(props) : null;
  }

  public boolean configured() {
    return vapid != null;
  }

  @Transactional(readOnly = true)
  public Status status() {
    return new Status(
        configured(), vapid == null ? null : vapid.publicKeyBase64(), subscriptions.count());
  }

  /**
   * Register a browser, or refresh one that is already registered: the endpoint is the identity.
   *
   * @throws BadRequestException when the keys are not the shape a browser produces, which means the
   *     client sent something other than what {@code pushManager.subscribe} returned
   */
  @Transactional
  public Subscribed subscribe(String endpoint, String p256dh, String auth, String userAgent) {
    requireOn();
    String target = requireEndpoint(endpoint);
    String publicKey = requireKey(p256dh, 65, "p256dh must be a base64url 65-byte P-256 point");
    String secret = requireKey(auth, 16, "auth must be a base64url 16-byte secret");
    String agent = userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
    PushSubscription row =
        subscriptions
            .findByEndpoint(target)
            .map(
                existing -> {
                  existing.replaceKeys(publicKey, secret, agent);
                  return existing;
                })
            .orElseGet(() -> new PushSubscription(target, publicKey, secret, agent));
    row = subscriptions.save(row);
    return new Subscribed(row.getId(), subscriptions.count());
  }

  @Transactional(readOnly = true)
  public List<Device> devices() {
    return subscriptions.findAllByOrderByCreatedAtAsc().stream().map(PushService::device).toList();
  }

  /**
   * @throws NoSuchElementException when there is no such row — a 404, so a device that was already
   *     removed from another screen says so rather than pretending
   */
  @Transactional
  public void unsubscribe(long id) {
    PushSubscription row =
        subscriptions
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("push subscription " + id));
    subscriptions.delete(row);
  }

  /** To every device. Off, or nobody subscribed, is an outcome of zeros, not an error. */
  public Outcome send(Notification notification) {
    if (!configured()) {
      return Outcome.NOTHING;
    }
    return deliver(subscriptions.findAllByOrderByCreatedAtAsc(), notification);
  }

  /**
   * To one device, by its endpoint — the test button, so the phone that pressed it is the one that
   * buzzes.
   *
   * @throws NoSuchElementException when that endpoint is not subscribed
   */
  public Outcome sendTo(String endpoint, Notification notification) {
    requireOn();
    PushSubscription row =
        subscriptions
            .findByEndpoint(endpoint == null ? "" : endpoint.trim())
            .orElseThrow(() -> new NoSuchElementException("this device is not subscribed"));
    return deliver(List.of(row), notification);
  }

  /** The test button with no endpoint: everyone. */
  public Outcome sendToAll(Notification notification) {
    requireOn();
    return deliver(subscriptions.findAllByOrderByCreatedAtAsc(), notification);
  }

  private Outcome deliver(List<PushSubscription> targets, Notification notification) {
    byte[] payload = toJson(notification).getBytes(StandardCharsets.UTF_8);
    int sent = 0;
    int gone = 0;
    int failed = 0;
    for (PushSubscription target : targets) {
      switch (deliverOne(target, payload, notification.ttlSeconds())) {
        case SENT -> {
          target.markSent();
          subscriptions.save(target);
          sent++;
        }
        case GONE -> {
          subscriptions.delete(target);
          gone++;
        }
        case FAILED -> failed++;
      }
    }
    return new Outcome(sent, gone, failed);
  }

  private enum Delivery {
    SENT,
    GONE,
    FAILED
  }

  private Delivery deliverOne(PushSubscription target, byte[] payload, long ttlSeconds) {
    String endpoint = target.getEndpoint();
    try {
      byte[] body =
          PayloadCipher.encrypt(
              payload, B64D.decode(target.getP256dh()), B64D.decode(target.getAuth()));
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Authorization", vapid.authorization(endpoint, Instant.now()));
      headers.put("Content-Type", "application/octet-stream");
      headers.put("Content-Encoding", "aes128gcm");
      headers.put("TTL", Long.toString(ttlSeconds));
      headers.put("Urgency", "normal");
      int status = transport.send(endpoint, headers, body);
      if (status == 201 || status == 200) {
        return Delivery.SENT;
      }
      if (status == 404 || status == 410) {
        log.info("Push subscription {} is gone ({}); removing it", abbreviate(endpoint), status);
        return Delivery.GONE;
      }
      log.warn("Push to {} answered {}; keeping the subscription", abbreviate(endpoint), status);
      return Delivery.FAILED;
    } catch (IOException e) {
      log.warn("Push to {} did not get through: {}", abbreviate(endpoint), e.getMessage());
      return Delivery.FAILED;
    } catch (IllegalArgumentException e) {
      // Stored keys that no longer decode: the row is useless, and will be replaced when the
      // browser subscribes again. Removing it stops the log filling every morning.
      log.warn("Push subscription {} has unusable keys; removing it", abbreviate(endpoint));
      return Delivery.GONE;
    }
  }

  private void requireOn() {
    if (!configured()) {
      throw new ServiceOffException(
          "push is off — set PUSH_VAPID_PUBLIC_KEY and PUSH_VAPID_PRIVATE_KEY on the deployment");
    }
  }

  private static String requireEndpoint(String endpoint) {
    String value = endpoint == null ? "" : endpoint.trim();
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("endpoint must be an https URL");
    }
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || value.length() > 2000) {
      throw new BadRequestException("endpoint must be an https URL");
    }
    return value;
  }

  private static String requireKey(String value, int bytes, String message) {
    String key = value == null ? "" : value.trim();
    try {
      if (B64D.decode(key).length != bytes) {
        throw new BadRequestException(message);
      }
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(message);
    }
    return key;
  }

  /** Endpoints are capabilities: enough of one to recognise it in a log, never the whole thing. */
  static String abbreviate(String endpoint) {
    int slash = endpoint.indexOf('/', "https://".length());
    String host = slash < 0 ? endpoint : endpoint.substring(0, slash);
    String tail = endpoint.length() > 8 ? endpoint.substring(endpoint.length() - 8) : endpoint;
    return host + "/…" + tail;
  }

  private static Device device(PushSubscription s) {
    return new Device(
        s.getId(),
        s.getUserAgent(),
        s.getCreatedAt().toString(),
        Optional.ofNullable(s.getLastSentAt()).map(Object::toString).orElse(null),
        s.getEndpoint());
  }

  private String toJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not serialize the notification", e);
    }
  }

  /**
   * What the phone shows. {@code tab} is where a tap lands; {@code tag} collapses duplicates on the
   * device, so a redrafted brief replaces the morning's notification rather than stacking on it;
   * {@code ttlSeconds} is how long the push service holds an undelivered message.
   */
  public record Notification(String title, String body, String tab, String tag, long ttlSeconds) {

    private static final long SIX_HOURS = 6 * 3600;
    private static final long ONE_DAY = 24 * 3600;

    /** A brief delivered at noon is noise, so it is held for six hours and then dropped. */
    public static Notification morningBrief(String headline) {
      return new Notification("morning brief", headline, "today", "morning-brief", SIX_HOURS);
    }

    /** The line to wake up to. Null when the brief had none, so the caller sends nothing. */
    public static Notification morningMotivation(String line) {
      if (line == null || line.isBlank()) {
        return null;
      }
      return new Notification(
          "good morning", line.trim(), "today", "morning-motivation", SIX_HOURS);
    }

    /** The day's readings, for reading together later. Held through the morning, then dropped. */
    public static Notification readings(String pieces) {
      return new Notification("today's readings", pieces, "recovery", "readings", SIX_HOURS);
    }

    /** What is still open. Replaces the previous reminder on the device rather than stacking. */
    public static Notification todosWaiting(String title, String names) {
      return new Notification(title, names, "todos", "todos", SIX_HOURS);
    }

    public static Notification weeklyReview() {
      return new Notification(
          "weekly review is ready",
          "Friday's review is drafted — read it on the week tab",
          "week",
          "weekly-review",
          ONE_DAY);
    }

    public static Notification test() {
      return new Notification(
          "notifications are on",
          "this is what the morning brief will look like",
          "today",
          "test",
          300);
    }
  }

  public record Status(boolean configured, String publicKey, long devices) {}

  public record Subscribed(long id, long devices) {}

  /**
   * @param endpoint returned so the browser can recognise its own row; it is the account's own
   *     data, and there is one account
   */
  public record Device(
      long id, String label, String createdAt, String lastSentAt, String endpoint) {}

  public record Outcome(int sent, int gone, int failed) {
    static final Outcome NOTHING = new Outcome(0, 0, 0);
  }
}
