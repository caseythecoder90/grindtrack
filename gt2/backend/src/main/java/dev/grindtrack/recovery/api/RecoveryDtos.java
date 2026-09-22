package dev.grindtrack.recovery.api;

import dev.grindtrack.recovery.domain.JournalEntry;

/** Request and response shapes for the recovery API that are not computed views. */
public final class RecoveryDtos {

  private RecoveryDtos() {}

  /** Either field may be null to leave it alone. */
  public record SettingsRequest(Integer pagesPerDay, Integer meditationMinutes) {}

  /** {@code completed} false is a sitting cut short: logged, but the day is not marked. */
  public record SessionRequest(Integer minutes, Boolean completed) {}

  public record JournalRequest(String body, Boolean spoken) {}

  public record JournalResponse(Long id, String createdAt, String body, boolean spoken) {

    public static JournalResponse from(JournalEntry entry) {
      return new JournalResponse(
          entry.getId(), entry.getCreatedAt().toString(), entry.getBody(), entry.isSpoken());
    }
  }

  /** A person to keep in touch with. {@code role} is sponsor, prospect or friend. */
  public record PersonRequest(String name, String role, Integer cadenceDays, String note) {}

  /** Partial update: a null field is left alone; {@code clearNote} removes the note. */
  public record PersonUpdateRequest(
      String name, String role, Integer cadenceDays, String note, Boolean clearNote) {}

  public record ContactRequest(String note) {}

  /** Where the reader is: a paragraph seq. */
  public record PlaceRequest(Integer seq) {}

  /**
   * @param start offset into the paragraph where the marked words begin; null with {@code end} null
   *     marks the whole paragraph
   */
  public record MarkRequest(Integer seq, Integer start, Integer end, String color, String note) {}

  public record MarkUpdateRequest(
      String color, Boolean clearColor, String note, Boolean clearNote) {}
}
