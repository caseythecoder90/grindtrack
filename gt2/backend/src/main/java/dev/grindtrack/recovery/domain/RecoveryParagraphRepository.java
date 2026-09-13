package dev.grindtrack.recovery.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecoveryParagraphRepository extends JpaRepository<RecoveryParagraph, Long> {

  /** A chapter as the table of contents sees it. */
  interface ChapterSummary {
    int getChapterNo();

    String getChapterTitle();

    int getFirstSeq();

    long getParagraphs();

    int getFirstPageSeq();

    int getLastPageSeq();
  }

  /**
   * From the cursor onwards, enough for any day: at five minutes a day and the Big Book's paragraph
   * lengths a day is ten to twenty paragraphs, and a very long setting is still under eighty.
   */
  List<RecoveryParagraph> findTop80ByTextIdAndSeqGreaterThanEqualOrderBySeqAsc(
      Long textId, int seq);

  List<RecoveryParagraph> findByTextIdAndSeqBetweenOrderBySeqAsc(Long textId, int from, int to);

  /**
   * The day's part: from the cursor, every paragraph that starts before the page after the last one
   * due.
   */
  List<RecoveryParagraph> findByTextIdAndSeqGreaterThanEqualAndPageSeqLessThanOrderBySeqAsc(
      Long textId, int seq, int pageSeqEnd);

  Optional<RecoveryParagraph> findByTextIdAndSeq(Long textId, int seq);

  List<RecoveryParagraph> findByTextIdAndChapterNoOrderBySeqAsc(Long textId, int chapterNo);

  /** The page label a page number prints as, from any paragraph on it. */
  Optional<RecoveryParagraph> findFirstByTextIdAndPageSeqOrderBySeqAsc(Long textId, int pageSeq);

  @Query(
      """
      select p.chapterNo as chapterNo, p.chapterTitle as chapterTitle,
             min(p.seq) as firstSeq, count(p) as paragraphs,
             min(p.pageSeq) as firstPageSeq, max(p.pageSeq) as lastPageSeq
      from RecoveryParagraph p where p.textId = :textId
      group by p.chapterNo, p.chapterTitle order by p.chapterNo
      """)
  List<ChapterSummary> chapters(Long textId);
}
