package dev.grindtrack.work.api;

import dev.grindtrack.work.api.WorkDtos.WorkDayRequest;
import dev.grindtrack.work.api.WorkDtos.WorkDayResponse;
import dev.grindtrack.work.api.WorkDtos.WorkSkillCreateRequest;
import dev.grindtrack.work.api.WorkDtos.WorkSkillResponse;
import dev.grindtrack.work.api.WorkDtos.WorkSkillUpdateRequest;
import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import dev.grindtrack.work.domain.WorkSkill;
import dev.grindtrack.work.domain.WorkSkillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 */
@RestController
@RequestMapping("/api/work")
public class WorkController {

  private static final int MAX_TEXT_CHARS = 10_000;
  private static final int MAX_CATEGORIES = 50;
  private static final Set<String> SKILL_STATUSES =
      Set.of("not_started", "in_progress", "proficient");

  private final WorkLogRepository workLogs;
  private final WorkSkillRepository workSkills;

  public WorkController(WorkLogRepository workLogs, WorkSkillRepository workSkills) {
    this.workLogs = workLogs;
    this.workSkills = workSkills;
  }

  // ---------- daily work logs ----------

  @GetMapping("/days")
  public ResponseEntity<?> range(@RequestParam String from, @RequestParam String to) {
    LocalDate fromDate = requireDate(from, "from and to must be YYYY-MM-DD");
    LocalDate toDate = requireDate(to, "from and to must be YYYY-MM-DD");
    List<WorkDayResponse> body =
        workLogs.findByLogDateBetweenOrderByLogDate(fromDate, toDate).stream()
            .map(WorkDayResponse::from)
            .toList();
    return ResponseEntity.ok(body);
  }

  @GetMapping("/days/{date}")
  public ResponseEntity<?> day(@PathVariable String date) {
    LocalDate parsed = requireDate(date, "invalid date");
    return ResponseEntity.ok(workLogs.findById(parsed).map(WorkDayResponse::from).orElse(null));
  }

  @PutMapping("/days/{date}")
  public ResponseEntity<?> upsertDay(@PathVariable String date, @RequestBody WorkDayRequest body) {
    LocalDate parsed = requireDate(date, "invalid date");
    BigDecimal hours = body.hours() == null ? BigDecimal.ZERO : body.hours();
    validateDayFields(hours, body);
    WorkLog log = workLogs.findById(parsed).orElseGet(() -> new WorkLog(parsed));
    log.update(
        hours,
        body.categories(),
        body.project(),
        body.goals(),
        body.did(),
        body.blockers(),
        body.learnings());
    workLogs.save(log);
    return ResponseEntity.ok(Map.of("saved", parsed.toString()));
  }

  @DeleteMapping("/days/{date}")
  public ResponseEntity<?> deleteDay(@PathVariable String date) {
    LocalDate parsed = requireDate(date, "invalid date");
    workLogs.deleteById(parsed);
    return ResponseEntity.ok(Map.of("deleted", parsed.toString()));
  }

  // ---------- skills / competencies ----------

  @GetMapping("/skills")
  public List<WorkSkillResponse> skills() {
    return workSkills.findAllByOrderBySortOrderAscIdAsc().stream()
        .map(WorkSkillResponse::from)
        .toList();
  }

  @PostMapping("/skills")
  public ResponseEntity<?> createSkill(@RequestBody WorkSkillCreateRequest body) {
    String name = body.name() == null ? "" : body.name().trim();
    if (name.isBlank() || name.length() > 200) {
      throw new BadRequest("a skill needs a name (max 200 chars)");
    }
    if (body.category() != null && body.category().length() > 40) {
      throw new BadRequest("category is limited to 40 characters");
    }
    if (tooLong(body.detail())) {
      throw new BadRequest("detail is limited to " + MAX_TEXT_CHARS + " characters");
    }
    int nextOrder = (int) workSkills.count();
    WorkSkill skill = new WorkSkill(name, body.category(), body.detail(), nextOrder);
    return ResponseEntity.ok(WorkSkillResponse.from(workSkills.save(skill)));
  }

  @PatchMapping("/skills/{id}")
  public ResponseEntity<?> updateSkill(
      @PathVariable Long id, @RequestBody WorkSkillUpdateRequest body) {
    if (body.status() != null && !SKILL_STATUSES.contains(body.status())) {
      throw new BadRequest("status must be one of " + SKILL_STATUSES);
    }
    if (body.name() != null && (body.name().isBlank() || body.name().length() > 200)) {
      throw new BadRequest("a skill needs a name (max 200 chars)");
    }
    if (body.category() != null && body.category().length() > 40) {
      throw new BadRequest("category is limited to 40 characters");
    }
    if (tooLong(body.detail(), body.notes())) {
      throw new BadRequest("text fields are limited to " + MAX_TEXT_CHARS + " characters");
    }
    return workSkills
        .findById(id)
        .<ResponseEntity<?>>map(
            skill -> {
              if (body.name() != null) {
                skill.setName(body.name().trim());
              }
              if (body.category() != null) {
                skill.setCategory(body.category());
              }
              if (body.detail() != null) {
                skill.setDetail(body.detail());
              }
              if (body.status() != null) {
                skill.setStatus(body.status());
              }
              if (body.notes() != null) {
                skill.setNotes(body.notes());
              }
              if (body.sortOrder() != null) {
                skill.setSortOrder(body.sortOrder());
              }
              return ResponseEntity.ok(WorkSkillResponse.from(workSkills.save(skill)));
            })
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "no such skill")));
  }

  @DeleteMapping("/skills/{id}")
  public ResponseEntity<?> deleteSkill(@PathVariable Long id) {
    workSkills.deleteById(id);
    return ResponseEntity.ok(Map.of("deleted", id));
  }

  // ---------- validation ----------

  private static void validateDayFields(BigDecimal hours, WorkDayRequest body) {
    if (hours.signum() < 0 || hours.doubleValue() > 24) {
      throw new BadRequest("hours must be 0-24");
    }
    if (body.categories() != null
        && (body.categories().size() > MAX_CATEGORIES
            || body.categories().stream().anyMatch(c -> c == null || c.length() > 100))) {
      throw new BadRequest("too many categories, or a category name over 100 chars");
    }
    if (body.project() != null && body.project().length() > 120) {
      throw new BadRequest("project is limited to 120 characters");
    }
    if (tooLong(body.goals(), body.did(), body.blockers(), body.learnings())) {
      throw new BadRequest("text fields are limited to " + MAX_TEXT_CHARS + " characters");
    }
  }

  private static boolean tooLong(String... values) {
    for (String value : values) {
      if (value != null && value.length() > MAX_TEXT_CHARS) {
        return true;
      }
    }
    return false;
  }

  private static LocalDate requireDate(String value, String message) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new BadRequest(message);
    }
  }

  @ExceptionHandler(BadRequest.class)
  ResponseEntity<Map<String, String>> onBadRequest(BadRequest e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  private static final class BadRequest extends RuntimeException {
    BadRequest(String message) {
      super(message);
    }
  }
}
