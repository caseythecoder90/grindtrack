package dev.grindtrack.assistant.service;

/**
 * What a call cost, in dollars, from what the API reported.
 *
 * <p>One place for the rates, because three services price their calls and a rate that lives in
 * three files is a rate that is wrong in one of them. Cached tokens are priced as cached tokens:
 * the API reports them in their own fields and leaves them out of {@code inputTokens}, so pricing a
 * month without them would understate the invoice.
 */
final class Costs {

  /** Opus 5 list price. The model is configuration; the price is not, and a change here is a PR. */
  static final double INPUT_USD_PER_MTOK = 5.00;

  static final double OUTPUT_USD_PER_MTOK = 25.00;

  /** Five-minute-TTL cache write, as a multiple of the base input rate. */
  static final double CACHE_WRITE_MULTIPLIER = 1.25;

  /** Cache read, as a multiple of the base input rate — the whole point of caching. */
  static final double CACHE_READ_MULTIPLIER = 0.1;

  private Costs() {}

  static double usd(
      long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {
    double dollars =
        inputTokens / 1_000_000.0 * INPUT_USD_PER_MTOK
            + cacheWriteTokens / 1_000_000.0 * INPUT_USD_PER_MTOK * CACHE_WRITE_MULTIPLIER
            + cacheReadTokens / 1_000_000.0 * INPUT_USD_PER_MTOK * CACHE_READ_MULTIPLIER
            + outputTokens / 1_000_000.0 * OUTPUT_USD_PER_MTOK;
    return round(dollars);
  }

  /**
   * What caching is worth, net — and it can be negative. A read saves 0.9x the base rate; a write
   * costs 0.25x extra on a token that may never be read back. A negative number means the turns are
   * too short or too far apart for the prefix to be reused, and the breakpoints should come out.
   */
  static double cacheSaving(long cacheWriteTokens, long cacheReadTokens) {
    double dollars =
        (cacheReadTokens * (1 - CACHE_READ_MULTIPLIER)
                - cacheWriteTokens * (CACHE_WRITE_MULTIPLIER - 1))
            / 1_000_000.0
            * INPUT_USD_PER_MTOK;
    return round(dollars);
  }

  /** Four decimal places: cents matter here, and a review is about three of them. */
  private static double round(double dollars) {
    return Math.round(dollars * 10_000.0) / 10_000.0;
  }
}
