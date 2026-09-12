package dev.grindtrack.speech.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * The one WebSocket endpoint. No allowed-origins list means Spring's default: the handshake's
 * Origin must match the host, which is exactly right for a same-origin app. The handler's timer
 * lives in {@code SpeechAsyncConfig}: a bean defined here would make this class and the handler
 * depend on each other.
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
}
