package dev.grindtrack.chat.api;

import dev.grindtrack.auth.security.SignedIn;
import dev.grindtrack.chat.api.ChatDtos.CursorRequest;
import dev.grindtrack.chat.api.ChatDtos.SendRequest;
import dev.grindtrack.chat.service.MediaService;
import dev.grindtrack.chat.service.RoomService;
import dev.grindtrack.web.BadRequestException;
import dev.grindtrack.web.Requests;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The chat over HTTP: the room's state, its history, every change to it, and its pictures. Shared
 * by both roles — it is the one place in the app a partner writes to. What changes here goes out
 * over the sockets in {@code ChatSocketHandler}; nothing is sent over a socket that could be a
 * request.
 */
@RestController
@RequestMapping("/api/chat")
public class RoomController {

  static final int MAX_BODY_CHARS = 4000;

  private final RoomService chat;
  private final MediaService media;

  public RoomController(RoomService chat, MediaService media) {
    this.chat = chat;
    this.media = media;
  }

  @GetMapping
  public RoomService.State state(Principal principal) {
    return chat.state(SignedIn.of(principal));
  }

  /**
   * No argument: the newest page. {@code before}: the page above it. {@code after}: the catch-up.
   */
  @GetMapping("/messages")
  public RoomService.Page messages(
      @RequestParam(required = false) Long before, @RequestParam(required = false) Long after) {
    return chat.history(before, after);
  }

  /**
   * Words need to be there unless a picture is; with one, the words are the caption and may be
   * none.
   */
  @PostMapping("/messages")
  public RoomService.MessageView send(@RequestBody SendRequest body, Principal principal) {
    String text =
        body.mediaId() == null
            ? Requests.requireText(body.body(), "message needs some words", MAX_BODY_CHARS)
            : caption(body.body());
    return chat.send(SignedIn.of(principal), clientId(body.clientId()), text, body.mediaId());
  }

  @DeleteMapping("/messages/{id}")
  public RoomService.MessageView unsend(@PathVariable long id, Principal principal) {
    return chat.unsend(SignedIn.of(principal), id);
  }

  @PutMapping("/messages/{id}/reactions/{emoji}")
  public RoomService.MessageView react(
      @PathVariable long id, @PathVariable String emoji, Principal principal) {
    return chat.react(SignedIn.of(principal), id, emoji, true);
  }

  @DeleteMapping("/messages/{id}/reactions/{emoji}")
  public RoomService.MessageView unreact(
      @PathVariable long id, @PathVariable String emoji, Principal principal) {
    return chat.react(SignedIn.of(principal), id, emoji, false);
  }

  /** Delivered and read, as far as this device has got. */
  @PostMapping("/cursor")
  public RoomService.CursorView cursor(@RequestBody CursorRequest body, Principal principal) {
    return chat.moveCursor(SignedIn.of(principal), body.deliveredUpTo(), body.readUpTo());
  }

  // ---- pictures and video --------------------------------------------------------------------

  /**
   * Whether there is a bucket, and how big one upload may be. Read before the attach button is
   * drawn.
   */
  @GetMapping("/media/status")
  public MediaService.Status mediaStatus() {
    return media.status();
  }

  /**
   * The upload, before the message: {@code file} (the photo or clip), {@code poster} (a small JPEG
   * the phone made: the thumbnail, or a frame), and the shape, so the thread can lay it out before
   * it loads.
   */
  @PostMapping("/media")
  public MediaService.MediaView upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "poster", required = false) MultipartFile poster,
      @RequestParam(required = false) Integer width,
      @RequestParam(required = false) Integer height,
      @RequestParam(required = false) Integer durationMs,
      Principal principal) {
    return media.upload(SignedIn.of(principal), file, poster, width, height, durationMs);
  }

  /** The bytes: a 302 to a link signed for ten minutes, which the browser follows on its own. */
  @GetMapping("/media/{id}")
  public ResponseEntity<Void> mediaBytes(@PathVariable long id) {
    return redirect(media.link(id, false));
  }

  /** The poster, or the object itself when it has none. */
  @GetMapping("/media/{id}/poster")
  public ResponseEntity<Void> poster(@PathVariable long id) {
    return redirect(media.link(id, true));
  }

  /** The tray: pictures either of you kept, newest first. */
  @GetMapping("/media/stickers")
  public List<MediaService.MediaView> stickers() {
    return media.stickers();
  }

  /** Keep a picture as a sticker: it is in the tray for both of you and can be sent again. */
  @PutMapping("/media/{id}/sticker")
  public MediaService.MediaView keepSticker(@PathVariable long id) {
    return media.setSticker(id, true);
  }

  /** Out of the tray. A picture no message shows any more is removed altogether. */
  @DeleteMapping("/media/{id}/sticker")
  public MediaService.MediaView dropSticker(@PathVariable long id) {
    return media.setSticker(id, false);
  }

  /** Never cached: the link inside expires, and the next ask gets a fresh one. */
  private static ResponseEntity<Void> redirect(URI to) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(to)
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private static String caption(String body) {
    String text = body == null ? "" : body.trim();
    if (text.length() > MAX_BODY_CHARS) {
      throw new BadRequestException("a caption is at most " + MAX_BODY_CHARS + " characters");
    }
    return text;
  }

  private static UUID clientId(String value) {
    try {
      return UUID.fromString(value == null ? "" : value.trim());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("clientId must be a UUID the phone made for this message");
    }
  }
}
