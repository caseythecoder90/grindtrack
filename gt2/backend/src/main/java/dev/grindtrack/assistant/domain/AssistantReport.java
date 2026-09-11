package dev.grindtrack.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One drafted report — today only ever a weekly review. Model output lands here and nowhere else; a
 * person copies it into the real review by clicking, or does not.
 */
@Entity
@Table(name = "assistant_reports")
public class AssistantReport {

  public static final String KIND_WEEKLY_REVIEW = "weekly_review";
  public static final String KIND_WEEK_PLAN = "week_plan";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String kind;

  @Column(name = "week_start", nullable = false)
  private LocalDate weekStart;

  @Column(name = "generated_at", nullable = false)
  private OffsetDateTime generatedAt;

  @Column(nullable = false)
  private String model;

  @Column(name = "input_tokens", nullable = false)
  private long inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private long outputTokens;

  @Column(name = "draft_json", nullable = false)
  private String draftJson;

  protected AssistantReport() {}

  public AssistantReport(String kind, LocalDate weekStart) {
    this.kind = kind;
    this.weekStart = weekStart;
  }

  /** A regeneration overwrites in place: one draft per week is the table's contract. */
  public void replaceDraft(String model, long inputTokens, long outputTokens, String draftJson) {
    this.model = model;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.draftJson = draftJson;
    this.generatedAt = OffsetDateTime.now();
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public OffsetDateTime getGeneratedAt() {
    return generatedAt;
  }

  public String getModel() {
    return model;
  }

  public long getInputTokens() {
    return inputTokens;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public String getDraftJson() {
    return draftJson;
  }
}
