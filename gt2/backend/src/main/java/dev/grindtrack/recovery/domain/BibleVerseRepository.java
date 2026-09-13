package dev.grindtrack.recovery.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BibleVerseRepository extends JpaRepository<BibleVerse, Long> {

  List<BibleVerse> findAllByOrderByBookOrdAscChapterAscVerseAsc();

  List<BibleVerse> findByBookAndChapterAndVerseBetweenOrderByVerseAsc(
      String book, int chapter, int from, int to);
}
