package dev.grindtrack.recovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.recovery.domain.BibleVerse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Passages of five to sixteen verses, cut at paragraphs, books in the configured order. */
class BiblePlanTest {

  /** A chapter of {@code n} verses with paragraph starts at the given verse numbers. */
  private static List<BibleVerse> chapter(String book, int chapter, int n, int... paras) {
    List<BibleVerse> out = new ArrayList<>();
    for (int v = 1; v <= n; v++) {
      boolean para = v == 1;
      for (int p : paras) {
        para |= p == v;
      }
      out.add(new BibleVerse(book, BibleBooks.ord(book), chapter, v, para, "v" + v));
    }
    return out;
  }

  @Test
  void aShortChapterIsOnePassageAndSaysSoInTheReference() {
    List<BiblePlan.Passage> plan = BiblePlan.build(chapter("PSA", 23, 6, 4), List.of("PSA"));

    assertThat(plan).hasSize(1);
    assertThat(plan.get(0).wholeChapter()).isTrue();
    assertThat(plan.get(0).reference()).isEqualTo("Psalm 23");
  }

  @Test
  void paragraphsCutOnceFiveVersesAreInAndSixteenIsTheCeiling() {
    // Paragraphs at 3, 8, 30: the first cut waits for five verses, the long stretch is cut at 16.
    List<BiblePlan.Passage> plan = BiblePlan.build(chapter("JHN", 3, 36, 3, 8, 30), List.of("JHN"));

    assertThat(plan)
        .extracting(p -> p.from() + "-" + p.to())
        .containsExactly("1-7", "8-23", "24-29", "30-36");
    assertThat(plan.get(1).reference()).isEqualTo("John 3:8–23");
    assertThat(plan).noneMatch(p -> p.to() - p.from() + 1 > BiblePlan.MAX_VERSES);
  }

  @Test
  void configuredBooksComeFirstThenTheRestInCanonicalOrder() {
    List<BibleVerse> verses = new ArrayList<>();
    verses.addAll(chapter("GEN", 1, 3));
    verses.addAll(chapter("PSA", 1, 3));
    verses.addAll(chapter("JHN", 1, 3));

    List<BiblePlan.Passage> plan = BiblePlan.build(verses, List.of("JHN", "PSA", "XYZ"));

    assertThat(plan).extracting(BiblePlan.Passage::book).containsExactly("JHN", "PSA", "GEN");
  }

  @Test
  void references() {
    assertThat(BibleBooks.reference("JHN", 3, 16, 16, false)).isEqualTo("John 3:16");
    assertThat(BibleBooks.reference("1PE", 5, 6, 11, false)).isEqualTo("1 Peter 5:6–11");
    assertThat(BibleBooks.ord("GEN")).isEqualTo(1);
    assertThat(BibleBooks.ord("REV")).isEqualTo(66);
    assertThat(BibleBooks.ord("TOB")).isZero();
  }
}
