package dev.grindtrack.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The speech relay's timer, kept apart from the WebSocket registration so the handler that needs it
 * and the configuration that registers the handler do not depend on each other.
 */
@Configuration
public class SpeechAsyncConfig {

  /** The stop-grace timer. One daemon thread; the work on it is a close. */
  @Bean(destroyMethod = "shutdownNow")
  public ScheduledExecutorService speechScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        r -> {
          Thread t = new Thread(r, "speech-timer");
          t.setDaemon(true);
          return t;
        });
  }
}
