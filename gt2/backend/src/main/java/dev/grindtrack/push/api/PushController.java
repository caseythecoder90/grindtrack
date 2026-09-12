package dev.grindtrack.push.api;

import dev.grindtrack.push.service.PushService;
import dev.grindtrack.web.Responses;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which devices get told things, and a button to prove one does.
 *
 * <p>All of it behind the session cookie: a subscription is a promise to send this account's data
 * to a device, so only the account may make one. Subscribing is a PUT because the endpoint is the
 * identity and doing it twice is the same as once.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

  private final PushService push;

  public PushController(PushService push) {
    this.push = push;
  }

  /** Whether push is configured, the public key a browser needs to subscribe, and how many have. */
  @GetMapping("/status")
  public PushService.Status status() {
    return push.status();
  }

  @GetMapping("/subscriptions")
  public List<PushService.Device> subscriptions() {
    return push.devices();
  }

  /**
   * The object {@code pushManager.subscribe} returned, as it is, plus what the browser calls
   * itself.
   */
  @PutMapping("/subscriptions")
  public PushService.Subscribed subscribe(@RequestBody SubscribeRequest body) {
    Keys keys = body.keys() == null ? new Keys(null, null) : body.keys();
    return push.subscribe(body.endpoint(), keys.p256dh(), keys.auth(), body.userAgent());
  }

  @DeleteMapping("/subscriptions/{id}")
  public Responses.Deleted unsubscribe(@PathVariable long id) {
    push.unsubscribe(id);
    return Responses.Deleted.of(id);
  }

  /**
   * Send the test notification: to the one device whose endpoint is given, so the phone that
   * pressed the button is the one that buzzes, or to every device when none is.
   */
  @PostMapping("/test")
  public PushService.Outcome test(@RequestBody(required = false) TestRequest body) {
    PushService.Notification test = PushService.Notification.test();
    if (body == null || body.endpoint() == null || body.endpoint().isBlank()) {
      return push.sendToAll(test);
    }
    return push.sendTo(body.endpoint(), test);
  }

  public record SubscribeRequest(String endpoint, Keys keys, String userAgent) {}

  public record Keys(String p256dh, String auth) {}

  public record TestRequest(String endpoint) {}
}
