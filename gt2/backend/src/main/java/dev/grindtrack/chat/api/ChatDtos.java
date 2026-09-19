package dev.grindtrack.chat.api;

/** Request shapes for the chat. The answers are {@code RoomService}'s records. */
public final class ChatDtos {

  private ChatDtos() {}

  /**
   * @param clientId a UUID the phone made before its first attempt; the same one on a retry
   * @param body the words, up to 4000 characters; may be empty when there is a {@code mediaId}
   * @param mediaId an upload of mine from {@code POST /media}, not yet on a message; absent for
   *     words alone
   */
  public record SendRequest(String clientId, String body, Long mediaId) {}

  /** Either or both; absent means unchanged. Neither ever moves backwards. */
  public record CursorRequest(Long deliveredUpTo, Long readUpTo) {}
}
