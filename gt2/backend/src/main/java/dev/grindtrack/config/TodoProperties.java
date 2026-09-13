package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.todos.*} block.
 *
 * @param reminderCron when the phone is reminded of what is still open. Spring cron, in the
 *     assistant's zone. The reminder fires only while something is open, so an empty list is a
 *     quiet day.
 */
@ConfigurationProperties(prefix = "grindtrack.todos")
public record TodoProperties(String reminderCron) {}
