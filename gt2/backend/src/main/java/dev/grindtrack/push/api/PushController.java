package dev.grindtrack.push.api;

import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.push.service.PushService;
import dev.grindtrack.web.Responses;
import java.security.Principal;
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
 * <p>All of it behind the session cookie and all of it the caller's own: a subscription is a
 * promise to send an account's data to a device, so it is made, listed, tested and removed by that
 * account and no other. Both roles reach this controller; the id in the principal is what keeps the
 * two apart. Subscribing is a PUT because the endpoint is the identity and doing it twice is the
 * same as once.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

  private final PushService push;

  public PushController(PushService push) {
    this.push = push;
  }

  /**
   * Whether push is configured, the public key a browser needs to subscribe, and how many of mine
   * have.
   */
  @GetMapping("/status")
  public PushService.Status status(Principal principal) {
    return push.status(SignedIn.of(principal).id());
  }

  @GetMapping("/subscriptions")
  public List<PushService.Device> subscriptions(Principal principal) {
    return push.devices(SignedIn.of(principal).id());
  }

  /**
   * The object {@code pushManager.subscribe} returned, as it is, plus what the browser calls
   * itself.
   */
  @PutMapping("/subscriptions")
  public PushService.Subscribed subscribe(@RequestBody SubscribeRequest body, Principal principal) {
    Keys keys = body.keys() == null ? new Keys(null, null) : body.keys();
    return push.subscribe(
        SignedIn.of(principal).id(), body.endpoint(), keys.p256dh(), keys.auth(), body.userAgent());
  }

  @DeleteMapping("/subscriptions/{id}")
  public Responses.Deleted unsubscribe(@PathVariable long id, Principal principal) {
    push.unsubscribe(SignedIn.of(principal).id(), id);
    return Responses.Deleted.of(id);
  }

  /**
   * Send the test notification: to the one device whose endpoint is given, so the phone that
   * pressed the button is the one that buzzes, or to every device of mine when none is.
   */
  @PostMapping("/test")
  public PushService.Outcome test(
      @RequestBody(required = false) TestRequest body, Principal principal) {
    long userId = SignedIn.of(principal).id();
    PushService.Notification test = PushService.Notification.test();
    if (body == null || body.endpoint() == null || body.endpoint().isBlank()) {
      return push.sendTo(userId, test);
    }
    return push.sendTo(userId, body.endpoint(), test);
  }

  public record SubscribeRequest(String endpoint, Keys keys, String userAgent) {}

  public record Keys(String p256dh, String auth) {}

  public record TestRequest(String endpoint) {}
}
