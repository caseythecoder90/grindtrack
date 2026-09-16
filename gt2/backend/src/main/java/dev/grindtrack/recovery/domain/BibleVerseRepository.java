package dev.grindtrack.recovery.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BibleVerseRepository extends JpaRepository<BibleVerse, Long> {

  List<BibleVerse> findAllByOrderByBookOrdAscChapterAscVerseAsc();

  List<BibleVerse> findByBookAndChapterAndVerseBetweenOrderByVerseAsc(
      String book, int chapter, int from, int to);

  List<BibleVerse> findByBookAndChapterOrderByVerseAsc(String book, int chapter);

  /**
   * Full-text search, the same shape as the book's: English stemming, every word required, best
   * first and then in canonical order, forty at most.
   */
  @Query(
      value =
          """
          select v.* from {h-schema}bible_verses v
          where to_tsvector('english', v.text) @@ plainto_tsquery('english', :q)
          order by ts_rank(to_tsvector('english', v.text), plainto_tsquery('english', :q)) desc,
                   v.book_ord, v.chapter, v.verse
          limit 40
          """,
      nativeQuery = true)
  List<BibleVerse> search(String q);
}
