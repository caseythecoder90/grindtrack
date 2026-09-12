package dev.grindtrack.assistant.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

  List<AssistantMessage> findByConversationIdOrderByIdAsc(Long conversationId);

  /** For the month's bill: chat spend counts the same as report spend. */
  List<AssistantMessage> findByCreatedAtGreaterThanEqual(OffsetDateTime since);

  /**
   * How long each conversation is, in one query rather than one per row.
   *
   * <p>The list needs this because a title alone cannot tell two conversations apart when both
   * began with the same question — and a retry begins with exactly the same question. Turn count is
   * the second signal after time: a thread of one turn is an attempt, not a conversation.
   */
  @Query("select m.conversationId, count(m) from AssistantMessage m group by m.conversationId")
  List<Object[]> turnCounts();

  /** Conversations that drafted a week somewhere in them — the list marks those. */
  @Query(
      "select distinct m.conversationId from AssistantMessage m"
          + " where m.proposedWeekStart is not null")
  List<Long> conversationsWithADraft();
}
