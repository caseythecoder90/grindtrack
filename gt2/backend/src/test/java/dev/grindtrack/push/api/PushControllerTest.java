package dev.grindtrack.push.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.grindtrack.push.service.PushService;
import dev.grindtrack.web.ApiExceptionHandler;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.ServiceOffException;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The five endpoints' shapes, and that the browser's subscription object is taken as it is. */
class PushControllerTest {

  private static final String ENDPOINT = "https://web.push.apple.com/QGb5zW0Z9nJ3fXkqLm2P";

  private PushService push;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    push = mock(PushService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new PushController(push))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void statusSaysWhetherItIsOnAndHandsOverThePublicKey() throws Exception {
    when(push.status()).thenReturn(new PushService.Status(true, "BP4z9K", 2));

    mvc.perform(get("/api/push/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.publicKey").value("BP4z9K"))
        .andExpect(jsonPath("$.devices").value(2));
  }

  /** The body is what {@code pushManager.subscribe} returned, nested keys and all. */
  @Test
  void subscribingTakesTheBrowsersObjectAsItIs() throws Exception {
    when(push.subscribe(ENDPOINT, "BCVxsr7N", "BTBZMqHH", "iPhone"))
        .thenReturn(new PushService.Subscribed(4, 1));

    mvc.perform(
            put("/api/push/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\""
                        + ENDPOINT
                        + "\",\"keys\":{\"p256dh\":\"BCVxsr7N\",\"auth\":\"BTBZMqHH\"},"
                        + "\"userAgent\":\"iPhone\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(4))
        .andExpect(jsonPath("$.devices").value(1));
  }

  @Test
  void aSubscriptionWithoutKeysIsA400WithASentenceAndOffIsA503() throws Exception {
    when(push.subscribe(any(), any(), any(), any()))
        .thenThrow(new BadRequestException("p256dh must be a base64url 65-byte P-256 point"));
    mvc.perform(
            put("/api/push/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("p256dh must be a base64url 65-byte P-256 point"));

    when(push.sendToAll(any())).thenThrow(new ServiceOffException("push is off"));
    mvc.perform(post("/api/push/test"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("push is off"));
  }

  @Test
  void removingADeviceAnswersDeletedAndAMissingOneIs404() throws Exception {
    mvc.perform(delete("/api/push/subscriptions/4"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"deleted\":4}"));
    verify(push).unsubscribe(4L);

    org.mockito.Mockito.doThrow(new NoSuchElementException("push subscription 9"))
        .when(push)
        .unsubscribe(9L);
    mvc.perform(delete("/api/push/subscriptions/9"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not found: push subscription 9"));
  }

  @Test
  void theTestGoesToTheDeviceNamedOrToEveryone() throws Exception {
    when(push.sendTo(any(), any())).thenReturn(new PushService.Outcome(1, 0, 0));
    mvc.perform(
            post("/api/push/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sent").value(1));
    verify(push).sendTo(any(), any());

    when(push.sendToAll(any())).thenReturn(new PushService.Outcome(2, 1, 0));
    mvc.perform(post("/api/push/test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sent").value(2))
        .andExpect(jsonPath("$.gone").value(1));
  }
}
