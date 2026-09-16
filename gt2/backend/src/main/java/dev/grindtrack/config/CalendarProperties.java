package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.calendar.*} block: the two pushes the calendar sends.
 *
 * @param reminderCron how often the day is scanned for a block about to start; every minute, so the
 *     reminder lands within a minute of when it is meant to
 * @param reminderMinutes how far ahead of a block's start the reminder goes
 * @param upkeepCron when the phone is told what upkeep is overdue or due today; fires only while
 *     something is
 */
@ConfigurationProperties(prefix = "grindtrack.calendar")
public record CalendarProperties(String reminderCron, int reminderMinutes, String upkeepCron) {}
