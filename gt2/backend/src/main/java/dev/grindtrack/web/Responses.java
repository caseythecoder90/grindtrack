package dev.grindtrack.web;

/**
 * The two acknowledgement bodies that are not specific to any feature.
 *
 * <p>Ten endpoints across six controllers each built their own {@code Map.of("deleted", id)} and
 * declared {@code ResponseEntity<?>} to carry it. A map is not a contract: nothing stops the
 * eleventh from writing {@code "removed"}, and neither the compiler nor a reader of the method
 * signature can tell what the caller will receive. These records serialise to exactly the same
 * JSON, but the shape is now stated in the return type.
 */
public final class Responses {

  private Responses() {}

  /**
   * {@code {"deleted": <id>}}.
   *
   * @param deleted the identifier that was removed — a numeric id on most resources, an ISO date on
   *     the day-keyed logs, which is why this is not typed more tightly
   */
  public record Deleted(Object deleted) {

    public static Deleted of(Object id) {
      return new Deleted(id);
    }
  }

  /**
   * {@code {"saved": "<date>"}} — the day-keyed upserts.
   *
   * <p>The value is the date the write actually landed on, which is not always the date that was
   * sent: a weekly review filed against a Wednesday is stored against that week's Monday, and
   * echoing the raw input back would hide it.
   */
  public record Saved(String saved) {

    public static Saved of(Object date) {
      return new Saved(date.toString());
    }
  }
}
