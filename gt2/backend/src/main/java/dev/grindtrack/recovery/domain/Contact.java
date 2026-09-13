package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** One call, or one conversation: when, and a line about it if there was one. */
@Entity
@Table(name = "recovery_contacts")
public class Contact {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "person_id", nullable = false)
  private Long personId;

  @Column(nullable = false)
  private OffsetDateTime at = OffsetDateTime.now();

  @Column(length = 300)
  private String note;

  protected Contact() {}

  public Contact(Long personId, String note) {
    this.personId = personId;
    this.note = note;
  }

  public Long getId() {
    return id;
  }

  public Long getPersonId() {
    return personId;
  }

  public OffsetDateTime getAt() {
    return at;
  }

  public String getNote() {
    return note;
  }
}
