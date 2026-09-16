package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * What a passage means, in plain words, written once by the model and kept. Keyed by the passage
 * ("JHN 3:16-21"), so the same passage on two phones, or next time round the plan, is one call.
 */
@Entity
@Table(name = "bible_notes")
public class BibleNote {

  @Id
  @Column(name = "passage_key", length = 30)
  private String passageKey;

  @Column(nullable = false)
  private String body;

  @Column(nullable = false, length = 60)
  private String model;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  protected BibleNote() {}

  public BibleNote(String passageKey, String body, String model) {
    this.passageKey = passageKey;
    this.body = body;
    this.model = model;
  }

  public String getPassageKey() {
    return passageKey;
  }

  public String getBody() {
    return body;
  }

  public String getModel() {
    return model;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
