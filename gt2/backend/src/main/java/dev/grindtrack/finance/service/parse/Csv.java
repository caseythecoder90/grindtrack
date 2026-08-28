package dev.grindtrack.finance.service.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC-4180-ish CSV reader.
 *
 * <p>Hand-rolled rather than pulled in as a dependency because the requirements are tiny and
 * specific: quoted fields containing commas ({@code "$2,980.76"}), doubled quotes as an escape, and
 * CRLF or LF line endings. Five of the six statement formats are plain enough that a split on
 * commas would almost work — Bank of America and Aidvantage are the two that would silently
 * corrupt, and silently corrupting money is the failure mode worth spending code to avoid.
 */
public final class Csv {

  private Csv() {}

  public static List<List<String>> parse(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        row.add(field.toString());
        field.setLength(0);
      } else if (c == '\n') {
        row.add(field.toString());
        rows.add(row);
        row = new ArrayList<>();
        field.setLength(0);
      } else if (c != '\r') {
        field.append(c);
      }
    }
    if (field.length() > 0 || !row.isEmpty()) {
      row.add(field.toString());
      rows.add(row);
    }

    // Drop blank lines — every export ends with at least one.
    rows.removeIf(r -> r.stream().allMatch(f -> f.isBlank()));
    return rows;
  }

  /** Reads a column by header name, tolerating short rows. Returns "" rather than null. */
  public static String at(List<String> row, int index) {
    if (index < 0 || index >= row.size()) {
      return "";
    }
    String value = row.get(index);
    return value == null ? "" : value.trim();
  }
}
