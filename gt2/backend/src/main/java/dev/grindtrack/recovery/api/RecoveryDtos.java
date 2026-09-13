package dev.grindtrack.recovery.api;

import dev.grindtrack.recovery.domain.JournalEntry;

/** Request and response shapes for the recovery API that are not computed views. */
public final class RecoveryDtos {

  private RecoveryDtos() {}

  /** Either field may be null to leave it alone. */
  public record SettingsRequest(Integer readMinutes, Integer meditationMinutes) {}

  /** {@code completed} false is a sitting cut short: logged, but the day is not marked. */
  public record SessionRequest(Integer minutes, Boolean completed) {}

  public record JournalRequest(String body, Boolean spoken) {}

  public record JournalResponse(Long id, String createdAt, String body, boolean spoken) {

    public static JournalResponse from(JournalEntry entry) {
      return new JournalResponse(
          entry.getId(), entry.getCreatedAt().toString(), entry.getBody(), entry.isSpoken());
    }
  }
}
