package dev.grindtrack.assistant.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantConversationRepository
    extends JpaRepository<AssistantConversation, Long> {
  List<AssistantConversation> findAllByOrderByLastMessageAtDesc();
}
