package dev.grindtrack.chat.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReactionRepository extends JpaRepository<ChatReaction, Long> {

  List<ChatReaction> findAllByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

  List<ChatReaction> findAllByMessageIdOrderByIdAsc(long messageId);

  Optional<ChatReaction> findByMessageIdAndUserIdAndEmoji(
      long messageId, long userId, String emoji);
}
