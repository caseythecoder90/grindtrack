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
}
