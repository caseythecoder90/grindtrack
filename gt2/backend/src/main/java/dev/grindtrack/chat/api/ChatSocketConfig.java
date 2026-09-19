package dev.grindtrack.chat.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the chat socket. {@code @EnableWebSocket} and the container's frame buffer live on
 * {@code SpeechSocketConfig}, the first socket in the app; every {@code WebSocketConfigurer} bean
 * is collected by it, so this one only has to say its path. Same-origin only, like the other.
 */
@Configuration
public class ChatSocketConfig implements WebSocketConfigurer {

  private final ChatSocketHandler handler;

  public ChatSocketConfig(ChatSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/api/chat/ws");
  }
}
