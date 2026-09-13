package dev.grindtrack.recovery.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/** The number, and the words around it. Day one is the sobriety date itself. */
public final class Milestones {

  private Milestones() {}

  public record Milestone(long days, String label) {}

  public static long daysSober(LocalDate sobrietyDate, LocalDate today) {
    return ChronoUnit.DAYS.between(sobrietyDate, today) + 1;
  }

  /** "2 years, 8 days", or "11 months, 3 days", or "23 days". */
  public static String spelledOut(LocalDate sobrietyDate, LocalDate today) {
    Period p = Period.between(sobrietyDate, today);
    StringBuilder out = new StringBuilder();
    if (p.getYears() > 0) {
      out.append(p.getYears()).append(p.getYears() == 1 ? " year" : " years");
    }
    if (p.getMonths() > 0) {
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(p.getMonths()).append(p.getMonths() == 1 ? " month" : " months");
    }
    if (p.getDays() > 0 || out.length() == 0) {
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(p.getDays()).append(p.getDays() == 1 ? " day" : " days");
    }
    return out.toString();
  }

  /**
   * The next number worth marking: the early ones people count (30, 60, 90 days, six months), every
   * year, and every two hundred and fifty days.
   */
  public static Milestone next(long daysSober) {
    long best = Long.MAX_VALUE;
    for (long m : new long[] {30, 60, 90, 180}) {
      if (m > daysSober) {
        best = Math.min(best, m);
      }
    }
    long year = 365;
    long nextYear = ((daysSober / year) + 1) * year;
    long nextStep = ((daysSober / 250) + 1) * 250;
    best = Math.min(best, Math.min(nextYear, nextStep));
    String label;
    if (best == 180) {
      label = "6 months";
    } else if (best == nextYear) {
      long years = best / year;
      label = years + (years == 1 ? " year" : " years");
    } else {
      label = best + " days";
    }
    return new Milestone(best, label);
  }
}
