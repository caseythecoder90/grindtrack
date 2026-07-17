package dev.grindtrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

  /** A clock the test can advance manually. */
  private static final class MutableClock extends Clock {
    private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

    void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }

    @Override
    public Instant instant() {
      return instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private final MutableClock clock = new MutableClock();
  private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

  @Test
  void allowsFiveAttemptsThenBlocksTheSixth() {
    for (int i = 0; i < 5; i++) {
      assertThat(limiter.allow("1.2.3.4")).as("attempt %d", i + 1).isTrue();
    }
    assertThat(limiter.allow("1.2.3.4")).isFalse();
    assertThat(limiter.allow("1.2.3.4")).isFalse();
  }

  @Test
  void recoversOnceTheWindowSlidesPast() {
    for (int i = 0; i < 5; i++) {
      limiter.allow("1.2.3.4");
    }
    assertThat(limiter.allow("1.2.3.4")).isFalse();

    clock.advanceSeconds(301);
    assertThat(limiter.allow("1.2.3.4")).isTrue();
  }

  @Test
  void staysBlockedJustInsideTheWindow() {
    for (int i = 0; i < 5; i++) {
      limiter.allow("1.2.3.4");
    }
    clock.advanceSeconds(299);
    assertThat(limiter.allow("1.2.3.4")).isFalse();
  }

  @Test
  void slidesRatherThanResets() {
    // 3 attempts now, 2 attempts 200s later; at t=310 only the first 3 have expired.
    for (int i = 0; i < 3; i++) {
      limiter.allow("1.2.3.4");
    }
    clock.advanceSeconds(200);
    limiter.allow("1.2.3.4");
    limiter.allow("1.2.3.4");
    clock.advanceSeconds(110);
    assertThat(limiter.allow("1.2.3.4")).isTrue(); // 2 recent + this one = 3
    assertThat(limiter.allow("1.2.3.4")).isTrue();
    assertThat(limiter.allow("1.2.3.4")).isTrue();
    assertThat(limiter.allow("1.2.3.4")).isFalse(); // back at 5 in window
  }

  @Test
  void tracksIpsIndependently() {
    for (int i = 0; i < 5; i++) {
      limiter.allow("1.2.3.4");
    }
    assertThat(limiter.allow("1.2.3.4")).isFalse();
    assertThat(limiter.allow("5.6.7.8")).isTrue();
  }

  @Test
  void blockedAttemptsDoNotExtendTheBlock() {
    for (int i = 0; i < 5; i++) {
      limiter.allow("1.2.3.4");
    }
    // Hammering while blocked must not add attempts to the window.
    clock.advanceSeconds(150);
    assertThat(limiter.allow("1.2.3.4")).isFalse();
    clock.advanceSeconds(151);
    assertThat(limiter.allow("1.2.3.4")).isTrue();
  }
}
