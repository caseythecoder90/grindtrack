package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Someone in recovery to keep in touch with: a sponsor, a sponsor still to be asked, a friend. Each
 * has a cadence in days; the calls are {@link Contact} rows. Archived rather than deleted, because
 * the calls are a record worth keeping.
 */
@Entity
@Table(name = "recovery_people")
public class Person {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, length = 12)
  private PersonRole role;

  @Column(name = "cadence_days", nullable = false)
  private int cadenceDays = 7;

  @Column(length = 300)
  private String note;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  protected Person() {}

  public Person(String name, PersonRole role, int cadenceDays, String note) {
    this.name = name;
    this.role = role;
    this.cadenceDays = cadenceDays;
    this.note = note;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public PersonRole getRole() {
    return role;
  }

  public int getCadenceDays() {
    return cadenceDays;
  }

  public String getNote() {
    return note;
  }

  public boolean isArchived() {
    return archived;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void setRole(PersonRole role) {
    this.role = role;
  }

  public void setCadenceDays(int days) {
    this.cadenceDays = days;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public void archive() {
    this.archived = true;
  }

  /** The question was asked and the answer was yes: a prospect becomes a sponsor. */
  public void asked() {
    if (role == PersonRole.PROSPECT) {
      role = PersonRole.SPONSOR;
    }
  }
}
