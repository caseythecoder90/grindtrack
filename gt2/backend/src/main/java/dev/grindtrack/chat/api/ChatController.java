package dev.grindtrack.chat.api;

import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.api.ChatDtos.CursorRequest;
import dev.grindtrack.chat.api.ChatDtos.SendRequest;
import dev.grindtrack.chat.service.ChatService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The chat over HTTP: the room's state, its history, and every change to it. Shared by both roles —
 * it is the one place in the app a partner writes to. What changes here goes out over the sockets
 * in {@code ChatSocketHandler}; nothing is sent over a socket that could be a request.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

  static final int MAX_BODY_CHARS = 4000;

  private final ChatService chat;

  public ChatController(ChatService chat) {
    this.chat = chat;
  }

  @GetMapping
  public ChatService.State state(Principal principal) {
    return chat.state(SignedIn.of(principal));
  }

  /**
   * No argument: the newest page. {@code before}: the page above it. {@code after}: the catch-up.
   */
  @GetMapping("/messages")
  public ChatService.Page messages(
      @RequestParam(required = false) Long before, @RequestParam(required = false) Long after) {
    return chat.history(before, after);
  }

  @PostMapping("/messages")
  public ChatService.MessageView send(@RequestBody SendRequest body, Principal principal) {
    String text = Requests.requireText(body.body(), "message needs some words", MAX_BODY_CHARS);
    return chat.send(SignedIn.of(principal), clientId(body.clientId()), text);
  }

  @DeleteMapping("/messages/{id}")
  public ChatService.MessageView unsend(@PathVariable long id, Principal principal) {
    return chat.unsend(SignedIn.of(principal), id);
  }

  @PutMapping("/messages/{id}/reactions/{emoji}")
  public ChatService.MessageView react(
      @PathVariable long id, @PathVariable String emoji, Principal principal) {
    return chat.react(SignedIn.of(principal), id, emoji, true);
  }

  @DeleteMapping("/messages/{id}/reactions/{emoji}")
  public ChatService.MessageView unreact(
      @PathVariable long id, @PathVariable String emoji, Principal principal) {
    return chat.react(SignedIn.of(principal), id, emoji, false);
  }

  /** Delivered and read, as far as this device has got. */
  @PostMapping("/cursor")
  public ChatService.CursorView cursor(@RequestBody CursorRequest body, Principal principal) {
    return chat.moveCursor(SignedIn.of(principal), body.deliveredUpTo(), body.readUpTo());
  }

  private static UUID clientId(String value) {
    try {
      return UUID.fromString(value == null ? "" : value.trim());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("clientId must be a UUID the phone made for this message");
    }
  }
}
