package dev.grindtrack.finance.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns a bank's raw description into something you can group by.
 *
 * <p>Without this, "how much do I spend on coffee" is unanswerable, because the same shop appears
 * as {@code Digital Card Purchase - TPA6121BAYCOFFEETEA TAMPA FL} one week and something slightly
 * different the next.
 *
 * <p>Every rule here comes from a pattern actually present in the downloaded statements:
 *
 * <ul>
 *   <li>Capital One deposit accounts prefix the real merchant with the transaction type and a dash:
 *       {@code Digital Card Purchase - WINGSTOP 1861 813 725 9464 FL}. 226 of 420 checking rows
 *       look like this, so the prefix has to go before anything else — otherwise every card
 *       purchase normalizes to the literal string "DEBIT CARD PURCHASE".
 *   <li>They also use {@code Withdrawal from X} / {@code Deposit from X} for ACH.
 *   <li>Payment processors prepend their own tag: {@code SQ *} (Square), {@code TST*} (Toast),
 *       {@code AMZN Mktp}.
 *   <li>Card networks append a phone number and a state: {@code ... 813 725 9464 FL}.
 * </ul>
 */
@Component
public class MerchantNormalizer {

  /** Capital One's "<type> - <merchant>" prefix. Must be stripped first. */
  private static final Pattern TYPE_PREFIX =
      Pattern.compile(
          "^(?:digital|debit|credit)\\s+card\\s+purchase\\s*-\\s*", Pattern.CASE_INSENSITIVE);

  private static final Pattern ACH_PREFIX =
      Pattern.compile(
          "^(?:withdrawal|deposit|payment)\\s+(?:from|to)\\s+", Pattern.CASE_INSENSITIVE);

  /**
   * Payment processors that prepend their own tag to the real merchant name: Square, Toast, PayPal.
   *
   * <p>Deliberately does NOT include Amazon. {@code AMAZON MKTPL*BF5EW8ZE2} is Amazon itself
   * selling something — the brand is the merchant, not a processor wrapping one — so stripping it
   * would leave the order reference as the merchant name.
   */
  private static final Pattern PROCESSOR_PREFIX =
      Pattern.compile("^(?:sq|tst|paypal|pp)\\s*\\*?\\s*", Pattern.CASE_INSENSITIVE);

  /** A trailing US state code, optionally after a city. */
  private static final Pattern TRAILING_STATE = Pattern.compile("\\s+[A-Z]{2}\\s*$");

  /** Phone numbers the networks append, in the several shapes they use. */
  private static final Pattern PHONE =
      Pattern.compile("\\b(?:\\d{3}[-\\s]?\\d{3}[-\\s]?\\d{4}|800[-\\s]?\\d{3}[-\\s]?\\d{4})\\b");

  /** Order ids and store numbers: AMZN Mktp US*2K4LM7, #00440, long digit runs. */
  private static final Pattern REFERENCE = Pattern.compile("[*#]\\S*|\\b\\d{4,}\\b");

  /**
   * A corporate suffix marks the end of the legal name — whatever follows is a city, a state or
   * billing noise. "CO" is deliberately absent: it collides with Colorado and with names like
   * "COFFEE CO", and getting those wrong is worse than leaving a suffix in.
   */
  private static final List<String> CORPORATE_SUFFIXES =
      List.of("LLC", "INC", "CORP", "LTD", "GMBH");

  /**
   * @return a short, stable merchant name, or null if nothing meaningful survives
   */
  public String normalize(String rawDescription) {
    if (rawDescription == null || rawDescription.isBlank()) {
      return null;
    }

    String s = rawDescription.trim();
    s = TYPE_PREFIX.matcher(s).replaceAll("");
    s = ACH_PREFIX.matcher(s).replaceAll("");
    s = PROCESSOR_PREFIX.matcher(s).replaceAll("");

    s = s.toUpperCase();
    s = PHONE.matcher(s).replaceAll(" ");
    s = REFERENCE.matcher(s).replaceAll(" ");
    s = TRAILING_STATE.matcher(s).replaceAll(" ");
    s = s.replaceAll("[^A-Z0-9& ]", " ").replaceAll("\\s+", " ").trim();

    // Truncate at the first corporate suffix, since the legal name ends there and everything
    // after it is location: "GET COVERED LLC NEW YORK" is the merchant "GET COVERED".
    // Never truncate to nothing — a name that starts with its own suffix keeps it.
    String[] words = s.split(" ");
    int end = words.length;
    for (int i = 1; i < words.length; i++) {
      if (CORPORATE_SUFFIXES.contains(words[i])) {
        end = i;
        break;
      }
    }

    // Three words is enough to identify a merchant and short enough to group reliably:
    // "WHOLEFDS ATL" stays distinct, "TST STEAMIES BURGER BA TELLURIDE" collapses sensibly.
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < Math.min(end, 3); i++) {
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(words[i]);
    }

    String result = out.toString().trim();
    return result.isEmpty() ? null : result;
  }
}
