package dev.grindtrack.relationship.domain;

/**
 * What kind of thing happened.
 *
 * <p>One typed enum rather than several tables, the same way {@code TxnType} lets one transactions
 * table cover purchases, transfers and payments. "When did we last…" is then a single query, and a
 * new kind later — a walk, a weekend away — is a new constant rather than a migration and a screen.
 *
 * <p>{@link #INTIMACY} is deliberately in the same list as everything else. It is not a separate
 * system with its own metrics; it is one more thing that happened on a date, and treating it that
 * way in the model is what keeps it from acquiring targets and streaks later.
 */
public enum MomentKind {
  /** Going out, or a deliberate evening in. The thing most people mean by "a date". */
  DATE_NIGHT,
  /** A note left somewhere she will find it. Cheap, and the easiest one to let slide. */
  NOTE_LEFT,
  GIFT_GIVEN,
  INTIMACY,
  /** A real conversation, not logistics. Worth recording separately from a date night. */
  CONVERSATION,
  TRIP,
  /** Anything else done on purpose: flowers, a chore taken off her plate, a lift somewhere. */
  GESTURE;

  /**
   * True for kinds held back behind the discreet toggle.
   *
   * <p>Only one so far, but the check belongs on the enum rather than as a string comparison in
   * three places on the frontend.
   */
  public boolean isPrivate() {
    return this == INTIMACY;
  }

  /** Kinds worth showing a "last time" figure for on the summary. */
  public static MomentKind[] recencyOrder() {
    return new MomentKind[] {DATE_NIGHT, INTIMACY, NOTE_LEFT, CONVERSATION, GESTURE, GIFT_GIVEN};
  }
}
