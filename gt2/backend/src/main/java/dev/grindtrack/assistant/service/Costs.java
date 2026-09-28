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

  /**
   * A model's list price: dollars per million input and output tokens, and the cache read rate as a
   * multiple of the input rate. The model is configuration; the prices are not, and a change here
   * is a PR.
   */
  record Rate(double inputUsdPerMtok, double outputUsdPerMtok, double cacheReadMultiplier) {}

  /** Five-minute-TTL cache write, as a multiple of the base input rate, on every model. */
  static final double CACHE_WRITE_MULTIPLIER = 1.25;

  static final Rate OPUS_5_5 = new Rate(4.00, 20.00, 0.05);
  static final Rate OPUS_5 = new Rate(5.00, 25.00, 0.1);
  static final Rate SONNET_5 = new Rate(2.00, 10.00, 0.1);
  static final Rate HAIKU_4_5 = new Rate(1.00, 5.00, 0.1);

  private Costs() {}

  /** The rate for a model id. An id not listed here is priced as the dearest one it could be. */
  static Rate rate(String model) {
    String id = model == null ? "" : model;
    if (id.startsWith("claude-opus-5-5")) {
      return OPUS_5_5;
    }
    if (id.startsWith("claude-sonnet")) {
      return SONNET_5;
    }
    if (id.startsWith("claude-haiku")) {
      return HAIKU_4_5;
    }
    return OPUS_5;
  }

  static double usd(
      String model,
      long inputTokens,
      long outputTokens,
      long cacheWriteTokens,
      long cacheReadTokens) {
    Rate r = rate(model);
    double dollars =
        inputTokens / 1_000_000.0 * r.inputUsdPerMtok()
            + cacheWriteTokens / 1_000_000.0 * r.inputUsdPerMtok() * CACHE_WRITE_MULTIPLIER
            + cacheReadTokens / 1_000_000.0 * r.inputUsdPerMtok() * r.cacheReadMultiplier()
            + outputTokens / 1_000_000.0 * r.outputUsdPerMtok();
    return round(dollars);
  }

  /**
   * What caching is worth, net — and it can be negative. A read saves most of the base rate; a
   * write costs 0.25x extra on a token that may never be read back. A negative number means the
   * turns are too short or too far apart for the prefix to be reused, and the breakpoints should
   * come out.
   */
  static double cacheSaving(String model, long cacheWriteTokens, long cacheReadTokens) {
    Rate r = rate(model);
    double dollars =
        (cacheReadTokens * (1 - r.cacheReadMultiplier())
                - cacheWriteTokens * (CACHE_WRITE_MULTIPLIER - 1))
            / 1_000_000.0
            * r.inputUsdPerMtok();
    return round(dollars);
  }

  /** Four decimal places: cents matter here, and a review is about three of them. */
  private static double round(double dollars) {
    return Math.round(dollars * 10_000.0) / 10_000.0;
  }
}
