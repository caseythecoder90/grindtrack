package dev.grindtrack.chat.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMediaRepository extends JpaRepository<ChatMedia, Long> {

  /** The tray, newest first. */
  List<ChatMedia> findAllByStickerTrueOrderByIdDesc();
}
