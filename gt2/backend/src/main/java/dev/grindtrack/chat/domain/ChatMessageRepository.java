package dev.grindtrack.chat.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  Optional<ChatMessage> findByClientId(UUID clientId);

  /** The newest page. */
  List<ChatMessage> findTop50ByOrderByIdDesc();

  /** The page before a message: scrolling up. */
  List<ChatMessage> findTop50ByIdLessThanOrderByIdDesc(long id);

  /** Everything since a message: a phone catching up after its socket was away. */
  List<ChatMessage> findTop500ByIdGreaterThanOrderByIdAsc(long id);

  Optional<ChatMessage> findTopByOrderByIdDesc();

  /** The other person's messages past a read cursor, unsent ones aside. */
  long countBySenderIdNotAndIdGreaterThanAndDeletedAtIsNull(long senderId, long id);

  /** Whether an upload is already on a message: one message per upload. */
  boolean existsByMediaId(Long mediaId);

  /**
   * Words in the conversation, newest first: a plain substring match, case-blind, so a half-typed
   * word and an emoji both find their messages. Forty is a screen.
   */
  List<ChatMessage> findTop40ByDeletedAtIsNullAndBodyContainingIgnoreCaseOrderByIdDesc(String q);

  /** Messages with a link in them, newest first — the links panel; the phone picks the URLs out. */
  List<ChatMessage> findTop100ByDeletedAtIsNullAndBodyContainingIgnoreCaseOrderByIdDesc(String q);

  /** Messages carrying a picture, a clip or a recording, newest first — the media panel. */
  List<ChatMessage> findTop60ByDeletedAtIsNullAndMediaIdIsNotNullOrderByIdDesc();

  /** The messages just before one, for opening the thread around it. */
  List<ChatMessage> findTop25ByIdLessThanOrderByIdDesc(long id);

  /** A message and the ones just after it. */
  List<ChatMessage> findTop26ByIdGreaterThanEqualOrderByIdAsc(long id);
}
