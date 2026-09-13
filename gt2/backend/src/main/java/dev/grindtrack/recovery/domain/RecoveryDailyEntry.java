package dev.grindtrack.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One dated entry of a book read by the calendar: a title and the page under it. */
@Entity
@Table(name = "recovery_daily_entries")
public class RecoveryDailyEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "text_id", nullable = false)
  private Long textId;

  @Column(nullable = false)
  private int month;

  @Column(nullable = false)
  private int day;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false)
  private String body;

  protected RecoveryDailyEntry() {}

  public RecoveryDailyEntry(Long textId, int month, int day, String title, String body) {
    this.textId = textId;
    this.month = month;
    this.day = day;
    this.title = title;
    this.body = body;
  }

  public Long getId() {
    return id;
  }

  public Long getTextId() {
    return textId;
  }

  public int getMonth() {
    return month;
  }

  public int getDay() {
    return day;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }
}
