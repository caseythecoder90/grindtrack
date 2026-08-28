package dev.grindtrack.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One pattern, one category.
 *
 * <p>Rules are what turn a pile of imported rows into an answer to "what do I spend on groceries".
 * They run in {@code priority} order and the first match wins, so a narrow rule can be placed ahead
 * of a broad one without either needing to know the other exists.
 *
 * <p>A rule never overwrites a category a person chose — that guard lives in {@link
 * Transaction#categorizeByRule}, and it is the reason correcting a row by hand is worth doing.
 */
@Entity
@Table(name = "finance_category_rules")
public class CategoryRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String pattern;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false)
  private MatchType matchType = MatchType.CONTAINS;

  @Column(nullable = false)
  private String category;

  @Column(nullable = false)
  private int priority = 100;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "hit_count", nullable = false)
  private int hitCount = 0;

  @Column(name = "last_applied")
  private OffsetDateTime lastApplied;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  protected CategoryRule() {}

  public CategoryRule(String pattern, MatchType matchType, String category, int priority) {
    this.pattern = pattern.trim();
    this.matchType = matchType == null ? MatchType.CONTAINS : matchType;
    this.category = category.trim();
    this.priority = priority;
  }

  /**
   * @param merchant the normalized merchant, upper-cased by the caller; may be null
   * @param rawDescription the bank's original wording, upper-cased by the caller
   */
  public boolean matches(String merchant, String rawDescription) {
    if (!active) {
      return false;
    }
    return (merchant != null && matchType.matches(pattern, merchant))
        || matchType.matches(pattern, rawDescription);
  }

  /** Records that this rule claimed a row, so rules that have gone stale are visible as such. */
  public void recordHits(int hits) {
    if (hits <= 0) {
      return;
    }
    this.hitCount += hits;
    this.lastApplied = OffsetDateTime.now();
  }

  public void update(
      String pattern, MatchType matchType, String category, int priority, boolean active) {
    this.pattern = pattern.trim();
    this.matchType = matchType == null ? MatchType.CONTAINS : matchType;
    this.category = category.trim();
    this.priority = priority;
    this.active = active;
    this.updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getPattern() {
    return pattern;
  }

  public MatchType getMatchType() {
    return matchType;
  }

  public String getCategory() {
    return category;
  }

  public int getPriority() {
    return priority;
  }

  public boolean isActive() {
    return active;
  }

  public int getHitCount() {
    return hitCount;
  }

  public OffsetDateTime getLastApplied() {
    return lastApplied;
  }
}
