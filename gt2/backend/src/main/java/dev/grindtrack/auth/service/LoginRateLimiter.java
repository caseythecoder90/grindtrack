package dev.grindtrack.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Minimal per-IP sliding-window limiter for /api/auth/login: 5 attempts per 5 minutes. A pocket
 * version of the request rate limiter from Stripe's "Scaling your API with rate limiters".
 */
@Component
public class LoginRateLimiter {

  private static final int MAX_ATTEMPTS = 5;
  private static final long WINDOW_SECONDS = 300;
  private static final int MAX_TRACKED_IPS = 10_000;

  private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
  private final Clock clock;

  public LoginRateLimiter() {
    this(Clock.systemUTC());
  }

  LoginRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public boolean allow(String ip) {
    Instant now = clock.instant();
    if (attempts.size() > MAX_TRACKED_IPS) {
      purgeExpired(now);
    }
    Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
    Deque<Instant> window = attempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
    synchronized (window) {
      while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
        window.pollFirst();
      }
      if (window.size() >= MAX_ATTEMPTS) {
        return false;
      }
      window.addLast(now);
      return true;
    }
  }

  /** Drops IPs whose newest attempt is already outside the window, bounding memory. */
  private void purgeExpired(Instant now) {
    Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
    attempts
        .entrySet()
        .removeIf(
            e -> {
              synchronized (e.getValue()) {
                Instant newest = e.getValue().peekLast();
                return newest == null || newest.isBefore(cutoff);
              }
            });
  }
}
