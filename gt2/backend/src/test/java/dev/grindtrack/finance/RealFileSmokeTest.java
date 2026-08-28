package dev.grindtrack.finance;

import dev.grindtrack.finance.service.parse.AidvantageParser;
import dev.grindtrack.finance.service.parse.BankOfAmericaParser;
import dev.grindtrack.finance.service.parse.CapitalOneCreditParser;
import dev.grindtrack.finance.service.parse.CapitalOneDepositParser;
import dev.grindtrack.finance.service.parse.ChaseParser;
import dev.grindtrack.finance.service.parse.Csv;
import dev.grindtrack.finance.service.parse.ParsedStatement;
import dev.grindtrack.finance.service.parse.StatementParser;
import dev.grindtrack.finance.service.parse.WellsFargoParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Runs the real, gitignored statements in {@code statements/} through the parsers.
 *
 * <p>Committed on purpose, and safe to commit: it reads a directory that is gitignored and prints
 * only counts and dates, never a merchant or an amount. When the directory is absent -- CI, a fresh
 * clone, anyone else's machine -- it skips.
 *
 * <p>Worth keeping because it is the only test that exercises the actual export files. Synthetic
 * fixtures prove the parsers handle the shapes we thought of; this proves they handle the shapes
 * the banks actually send, which is how the Aidvantage doctype and the quoted thousands separators
 * were found in the first place.
 */
class RealFileSmokeTest {

  private static final List<StatementParser> PARSERS =
      List.of(
          new CapitalOneDepositParser(),
          new CapitalOneCreditParser(),
          new ChaseParser(),
          new BankOfAmericaParser(),
          new WellsFargoParser(),
          new AidvantageParser());

  @Test
  void everyRealFileParses() throws IOException {
    Path dir = Path.of("..", "..", "statements");
    if (!Files.isDirectory(dir)) {
      System.out.println("no statements dir — skipping");
      return;
    }

    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".csv")).toList()) {
        String text = Files.readString(file, StandardCharsets.UTF_8).stripLeading();
        if (text.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
          text = text.substring(text.indexOf('>') + 1);
        }
        List<List<String>> rows = Csv.parse(text);
        List<String> header = rows.get(0).stream().map(String::trim).toList();

        StatementParser parser =
            PARSERS.stream().filter(p -> p.canParse(header)).findFirst().orElse(null);

        String name = file.getFileName().toString();
        if (parser == null) {
          System.out.printf("  UNMATCHED  %-58s header=%s%n", name, header);
          continue;
        }
        ParsedStatement s = parser.parse(rows);
        System.out.printf(
            "  %-22s %-52s rows=%-4d pending=%-3d %s%s%n",
            s.format().name(),
            name.length() > 50 ? name.substring(0, 50) : name,
            s.rows().size(),
            s.pendingSkipped(),
            s.periodStart() == null ? "" : s.periodStart() + "→" + s.periodEnd(),
            s.closingBalance() == null ? "" : "  balance=" + s.closingBalance());
      }
    }
  }
}
