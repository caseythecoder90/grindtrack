package dev.grindtrack.work.api;

import dev.grindtrack.web.Requests;
import dev.grindtrack.work.api.WorkDtos.WorkDayRequest;
import dev.grindtrack.work.api.WorkDtos.WorkDayResponse;
import dev.grindtrack.work.api.WorkDtos.WorkSkillCreateRequest;
import dev.grindtrack.work.api.WorkDtos.WorkSkillResponse;
import dev.grindtrack.work.api.WorkDtos.WorkSkillUpdateRequest;
import dev.grindtrack.work.service.WorkService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Day-job tracking: the daily 40h/week accountability log plus the deliberate skill/competency
 * checklist. Mirrors the shape of {@code TrackingController} (the personal study tracker) but is a
 * separate resource under {@code /api/work} so work and study never mix.
 *
 * <p>Parsing and length limits live here because they are facts about a request. The rules about
 * what a work log may contain live in {@link WorkService}, because they hold whether the log
 * arrives over HTTP or any other way.
 */
@RestController
@RequestMapping("/api/work")
public class WorkController {

  private static final int MAX_TEXT_CHARS = 10_000;
  private static final int MAX_NAME_CHARS = 200;
  private static final int MAX_CATEGORY_CHARS = 40;
  private static final int MAX_PROJECT_CHARS = 120;

  private static final String TEXT_TOO_LONG =
      "text fields are limited to " + MAX_TEXT_CHARS + " characters";

  private final WorkService work;

  public WorkController(WorkService work) {
    this.work = work;
  }

  // ---------- daily work logs ----------

  @GetMapping("/days")
  public List<WorkDayResponse> range(@RequestParam String from, @RequestParam String to) {
    String message = "from and to must be YYYY-MM-DD";
    return work
        .daysBetween(Requests.requireDate(from, message), Requests.requireDate(to, message))
        .stream()
        .map(WorkDayResponse::from)
        .toList();
  }

  @GetMapping("/days/{date}")
  public ResponseEntity<?> day(@PathVariable String date) {
    return ResponseEntity.ok(
        work.day(Requests.requireDate(date, "invalid date"))
            .map(WorkDayResponse::from)
            .orElse(null));
  }

  @PutMapping("/days/{date}")
  public ResponseEntity<?> upsertDay(@PathVariable String date, @RequestBody WorkDayRequest body) {
    LocalDate parsed = Requests.requireDate(date, "invalid date");
    Requests.requireWithin(
        MAX_TEXT_CHARS, TEXT_TOO_LONG, body.goals(), body.did(), body.blockers(), body.learnings());
    Requests.requireWithin(
        MAX_PROJECT_CHARS,
        "project is limited to " + MAX_PROJECT_CHARS + " characters",
        body.project());

    work.saveDay(
        parsed,
        body.hours(),
        body.categories(),
        body.project(),
        body.goals(),
        body.did(),
        body.blockers(),
        body.learnings());
    return ResponseEntity.ok(Map.of("saved", parsed.toString()));
  }

  @DeleteMapping("/days/{date}")
  public ResponseEntity<?> deleteDay(@PathVariable String date) {
    LocalDate parsed = Requests.requireDate(date, "invalid date");
    work.deleteDay(parsed);
    return ResponseEntity.ok(Map.of("deleted", parsed.toString()));
  }

  // ---------- skills / competencies ----------

  @GetMapping("/skills")
  public List<WorkSkillResponse> skills() {
    return work.skills().stream().map(WorkSkillResponse::from).toList();
  }

  @PostMapping("/skills")
  public WorkSkillResponse createSkill(@RequestBody WorkSkillCreateRequest body) {
    String name = Requests.requireText(body.name(), "skill needs a name", MAX_NAME_CHARS);
    Requests.requireWithin(
        MAX_CATEGORY_CHARS,
        "category is limited to " + MAX_CATEGORY_CHARS + " characters",
        body.category());
    Requests.requireWithin(
        MAX_TEXT_CHARS, "detail is limited to " + MAX_TEXT_CHARS + " characters", body.detail());

    return WorkSkillResponse.from(work.createSkill(name, body.category(), body.detail()));
  }

  @PatchMapping("/skills/{id}")
  public ResponseEntity<?> updateSkill(
      @PathVariable Long id, @RequestBody WorkSkillUpdateRequest body) {
    if (body.name() != null) {
      Requests.requireText(body.name(), "skill needs a name", MAX_NAME_CHARS);
    }
    Requests.requireWithin(
        MAX_CATEGORY_CHARS,
        "category is limited to " + MAX_CATEGORY_CHARS + " characters",
        body.category());
    Requests.requireWithin(MAX_TEXT_CHARS, TEXT_TOO_LONG, body.detail(), body.notes());

    return work.updateSkill(
            id,
            body.name() == null ? null : body.name().trim(),
            body.category(),
            body.detail(),
            body.status(),
            body.notes(),
            body.sortOrder())
        .<ResponseEntity<?>>map(skill -> ResponseEntity.ok(WorkSkillResponse.from(skill)))
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "no such skill")));
  }

  @DeleteMapping("/skills/{id}")
  public ResponseEntity<?> deleteSkill(@PathVariable Long id) {
    work.deleteSkill(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }
}
