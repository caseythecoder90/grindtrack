package dev.grindtrack.speech.api;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * The one WebSocket endpoint. No allowed-origins list means Spring's default: the handshake's
 * Origin must match the host, which is exactly right for a same-origin app.
 */
@Configuration
@EnableWebSocket
public class SpeechSocketConfig implements WebSocketConfigurer {

  private final SpeechSocketHandler handler;

  public SpeechSocketConfig(SpeechSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/api/speech/ws");
  }

  /** Tomcat's default frame buffer is 8 KB; an audio frame is a few, and a burst may be more. */
  @Bean
  public ServletServerContainerFactoryBean speechSocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxBinaryMessageBufferSize(64 * 1024);
    container.setMaxTextMessageBufferSize(64 * 1024);
    return container;
  }

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
