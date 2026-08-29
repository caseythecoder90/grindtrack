package dev.grindtrack.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FocusKindTest {

  @Test
  void parsesTheLowerCaseWireSpelling() {
    assertThat(FocusKind.of("study")).isEqualTo(FocusKind.STUDY);
    assertThat(FocusKind.of("work")).isEqualTo(FocusKind.WORK);
  }

  @Test
  void isCaseInsensitiveAndIgnoresSurroundingSpace() {
    assertThat(FocusKind.of(" Work ")).isEqualTo(FocusKind.WORK);
  }

  /**
   * The reason this enum exists. The string version coerced anything unrecognised to "study", so a
   * typo logged work hours against the study log instead of failing — the exact confusion the split
   * between the two logs is there to prevent.
   */
  @Test
  void rejectsAnythingElseRatherThanFallingBackToStudy() {
    assertThatThrownBy(() -> FocusKind.of("personal")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundTripsThroughTheDatabaseColumnUnchanged() {
    FocusKind.JpaConverter converter = new FocusKind.JpaConverter();
    for (FocusKind kind : FocusKind.values()) {
      String column = converter.convertToDatabaseColumn(kind);
      // Existing rows hold "study"/"work"; a change of case here is a silent data migration.
      assertThat(column).isEqualTo(kind.name().toLowerCase());
      assertThat(converter.convertToEntityAttribute(column)).isEqualTo(kind);
    }
  }

  @Test
  void passesNullThroughBothWays() {
    FocusKind.JpaConverter converter = new FocusKind.JpaConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
