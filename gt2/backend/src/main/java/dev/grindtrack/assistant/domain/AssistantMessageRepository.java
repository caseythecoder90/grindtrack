package dev.grindtrack.assistant.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

  List<AssistantMessage> findByConversationIdOrderByIdAsc(Long conversationId);

  /** For the month's bill: chat spend counts the same as report spend. */
  List<AssistantMessage> findByCreatedAtGreaterThanEqual(OffsetDateTime since);
}
