package dev.grindtrack.work.api;

import dev.grindtrack.work.domain.WorkLog;
import dev.grindtrack.work.domain.WorkSkill;
import java.math.BigDecimal;
import java.util.List;

/** Request/response shapes for the work-tracking API. */
public final class WorkDtos {

  private WorkDtos() {}

  public record WorkDayRequest(
      BigDecimal hours,
      List<String> categories,
      String project,
      String goals,
      String did,
      String blockers,
      String learnings) {}

  public record WorkDayResponse(
      String logDate,
      BigDecimal hours,
      List<String> categories,
      String project,
      String goals,
      String did,
      String blockers,
      String learnings) {

    public static WorkDayResponse from(WorkLog log) {
      return new WorkDayResponse(
          log.getLogDate().toString(),
          log.getHours(),
          log.categoryList(),
          log.getProject(),
          log.getGoals(),
          log.getDid(),
          log.getBlockers(),
          log.getLearnings());
    }
  }

  /** Create payload for a work skill (name required; status defaults to not_started). */
  public record WorkSkillCreateRequest(String name, String category, String detail) {}

  /** Partial update: any null field is left unchanged. */
  public record WorkSkillUpdateRequest(
      String name,
      String category,
      String detail,
      String status,
      String notes,
      Integer sortOrder) {}

  public record WorkSkillResponse(
      Long id,
      String name,
      String category,
      String detail,
      String status,
      String notes,
      int sortOrder) {

    public static WorkSkillResponse from(WorkSkill skill) {
      return new WorkSkillResponse(
          skill.getId(),
          skill.getName(),
          skill.getCategory(),
          skill.getDetail(),
          skill.getStatus(),
          skill.getNotes(),
          skill.getSortOrder());
    }
  }
}
