package dev.grindtrack.chat.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCursorRepository extends JpaRepository<ChatCursor, Long> {}
