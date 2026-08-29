package dev.grindtrack.work.service;

import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkLogRepository;
import dev.grindtrack.work.domain.WorkSkill;
import dev.grindtrack.work.domain.WorkSkillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day-job hours and the deliberate skill checklist.
 *
 * <p>Extracted from {@code WorkController}, which had been talking to two repositories directly.
 * The rules that moved here are the ones that are true about a work log regardless of how it
 * arrives — hours cannot exceed a day, a log cannot carry fifty categories — and they were
 * previously unreachable from any test that did not go through HTTP.
 */
@Service
public class WorkService {

  /** A log listing more categories than this is a paste accident, not a day of work. */
  private static final int MAX_CATEGORIES = 50;

  private static final int MAX_CATEGORY_CHARS = 100;

  private static final Set<String> SKILL_STATUSES =
      Set.of("not_started", "in_progress", "proficient");

  private final WorkLogRepository workLogs;
  private final WorkSkillRepository workSkills;

  public WorkService(WorkLogRepository workLogs, WorkSkillRepository workSkills) {
    this.workLogs = workLogs;
    this.workSkills = workSkills;
  }

  // ------------------------------------------------------------------- days

  public List<WorkLog> daysBetween(LocalDate from, LocalDate to) {
    return workLogs.findByLogDateBetweenOrderByLogDate(from, to);
  }

  public Optional<WorkLog> day(LocalDate date) {
    return workLogs.findById(date);
  }

  /** Creates or updates the log for one day. The date is the primary key, so this is an upsert. */
  @Transactional
  public WorkLog saveDay(
      LocalDate date,
      BigDecimal hours,
      List<String> categories,
      String project,
      String goals,
      String did,
      String blockers,
      String learnings) {

    BigDecimal safeHours = hours == null ? BigDecimal.ZERO : hours;
    if (safeHours.signum() < 0 || safeHours.doubleValue() > 24) {
      throw new IllegalArgumentException("hours must be 0-24");
    }
    if (categories != null
        && (categories.size() > MAX_CATEGORIES
            || categories.stream().anyMatch(c -> c == null || c.length() > MAX_CATEGORY_CHARS))) {
      throw new IllegalArgumentException(
          "too many categories, or a category name over " + MAX_CATEGORY_CHARS + " chars");
    }

    WorkLog log = workLogs.findById(date).orElseGet(() -> new WorkLog(date));
    log.update(safeHours, categories, project, goals, did, blockers, learnings);
    return workLogs.save(log);
  }

  @Transactional
  public void deleteDay(LocalDate date) {
    workLogs.deleteById(date);
  }

  // ----------------------------------------------------------------- skills

  public List<WorkSkill> skills() {
    return workSkills.findAllByOrderBySortOrderAscIdAsc();
  }

  @Transactional
  public WorkSkill createSkill(String name, String category, String detail) {
    // New skills go to the end of the list, which is where you would expect to find one you just
    // added rather than at the top pushing everything down.
    int nextOrder = (int) workSkills.count();
    return workSkills.save(new WorkSkill(name, category, detail, nextOrder));
  }

  /**
   * Partial update: every argument is optional and null means "leave alone".
   *
   * <p>Returns empty rather than throwing when the id is unknown, so the controller decides what
   * absence looks like over HTTP. That is a transport question, not a domain one.
   */
  @Transactional
  public Optional<WorkSkill> updateSkill(
      Long id,
      String name,
      String category,
      String detail,
      String status,
      String notes,
      Integer sortOrder) {

    if (status != null && !SKILL_STATUSES.contains(status)) {
      throw new IllegalArgumentException("status must be one of " + SKILL_STATUSES);
    }

    return workSkills
        .findById(id)
        .map(
            skill -> {
              if (name != null) {
                skill.setName(name);
              }
              if (category != null) {
                skill.setCategory(category);
              }
              if (detail != null) {
                skill.setDetail(detail);
              }
              if (status != null) {
                skill.setStatus(status);
              }
              if (notes != null) {
                skill.setNotes(notes);
              }
              if (sortOrder != null) {
                skill.setSortOrder(sortOrder);
              }
              return workSkills.save(skill);
            });
  }

  @Transactional
  public void deleteSkill(Long id) {
    workSkills.deleteById(id);
  }
}
