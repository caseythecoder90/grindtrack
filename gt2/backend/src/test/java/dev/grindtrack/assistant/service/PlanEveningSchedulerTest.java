package dev.grindtrack.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.grindtrack.push.service.PushService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The evening line: the week's hours, the next target, tomorrow's block — from the numbers. */
class PlanEveningSchedulerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

  private static AssistantContext context(
      AssistantContext.Quarter quarter,
      double studyHours,
      List<AssistantContext.PlanItemSummary> inFlight,
      List<AssistantContext.PlanItemSummary> upcoming,
      List<AssistantContext.EventSummary> events) {
    return new AssistantContext(
        TODAY.toString(),
        quarter,
        new AssistantContext.Week("2026-09-14", studyHours, 15, 12, 28, 3),
        new AssistantContext.PlannedVsActual(9, studyHours, studyHours - 9),
        new AssistantContext.Lunch(2, 2, 5, 1.5, List.of()),
        inFlight,
        upcoming,
        events,
        List.of(),
        List.of(),
        List.of(),
        null);
  }

  private static AssistantContext.PlanItemSummary item(String title, String date) {
    return new AssistantContext.PlanItemSummary(1L, "milestone", title, "in_progress", "", date);
  }

  private static AssistantContext.EventSummary event(
      String date, String start, String title, String kind) {
    return new AssistantContext.EventSummary(1L, date, start, title, kind, null);
  }

  @Test
  void noPlanMeansNoPush() {
    assertThat(
            PlanEveningScheduler.notification(
                context(null, 4, List.of(), List.of(), List.of()), TODAY))
        .isNull();
  }

  @Test
  void theLineHasTheWeekTheNextTargetAndTomorrowsFirstBlock() {
    AssistantContext.Quarter q = new AssistantContext.Quarter(1, 1, "Jul–Sep 2026", "CKA", "", "");
    AssistantContext ctx =
        context(
            q,
            7.5,
            List.of(item("CKA course + labs", "2026-11-30"), item("CKA exam", "2026-10-09")),
            List.of(item("Java certification", "2027-01-15"), item("old target", "2026-09-01")),
            List.of(
                event("2026-09-17", "05:30:00", "work", "work_block"),
                event("2026-09-17", "05:30:00", "etcd lab", "study_block"),
                event("2026-09-17", "09:00:00", "dentist", "appointment")));

    PushService.Notification n = PlanEveningScheduler.notification(ctx, TODAY);

    assertThat(n.title()).isEqualTo("week 12 of the plan");
    assertThat(n.body())
        .isEqualTo(
            "7.5 of 15 h this week · CKA exam · Oct 9 · in 23 days · tomorrow 05:30 · etcd lab");
    assertThat(n.tab()).isEqualTo("plan");
    assertThat(n.tag()).isEqualTo("plan-evening");
  }

  @Test
  void withNothingDatedOrBookedTheLineStillStands() {
    AssistantContext.Quarter q = new AssistantContext.Quarter(1, 1, "Jul–Sep 2026", "CKA", "", "");
    AssistantContext ctx =
        context(q, 8, List.of(item("CKA course + labs", null)), List.of(), List.of());
    assertThat(PlanEveningScheduler.notification(ctx, TODAY).body())
        .isEqualTo("8 of 15 h this week · nothing booked tomorrow");
  }

  @Test
  void aTargetDueTomorrowSaysTomorrow() {
    AssistantContext.Quarter q = new AssistantContext.Quarter(1, 1, "Jul–Sep 2026", "CKA", "", "");
    AssistantContext ctx =
        context(q, 0, List.of(item("CKA exam", "2026-09-17")), List.of(), List.of());
    assertThat(PlanEveningScheduler.notification(ctx, TODAY).body())
        .isEqualTo("0 of 15 h this week · CKA exam · Sep 17 · tomorrow · nothing booked tomorrow");
  }
}
