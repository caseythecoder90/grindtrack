package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A journal entry: when, the words, and whether they were spoken. Nothing else. */
@Entity
@Table(name = "recovery_journal")
public class JournalEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(nullable = false)
  private String body;

  @Column(nullable = false)
  private boolean spoken;

  protected JournalEntry() {}

  public JournalEntry(String body, boolean spoken) {
    this.body = body;
    this.spoken = spoken;
  }

  public Long getId() {
    return id;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public String getBody() {
    return body;
  }

  public boolean isSpoken() {
    return spoken;
  }
}
