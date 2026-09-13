package dev.grindtrack.recovery.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryParagraphRepository extends JpaRepository<RecoveryParagraph, Long> {

  /** A chapter as the table of contents sees it. */
  interface ChapterSummary {
    int getChapterNo();

    String getChapterTitle();

    int getFirstSeq();

    long getParagraphs();
  }

  /**
   * From the cursor onwards, enough for any day: at five minutes a day and the Big Book's paragraph
   * lengths a day is ten to twenty paragraphs, and a very long setting is still under eighty.
   */
  List<RecoveryParagraph> findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(
      Long textId, int seq);

  List<RecoveryParagraph> findByTextIdAndSeqBetweenOrderBySeqAsc(Long textId, int from, int to);

  @Query(
      """
      select p.chapterNo as chapterNo, p.chapterTitle as chapterTitle,
             min(p.seq) as firstSeq, count(p) as paragraphs
      from RecoveryParagraph p where p.textId = :textId
      group by p.chapterNo, p.chapterTitle order by p.chapterNo
      """)
  List<ChapterSummary> chapters(Long textId);
}
