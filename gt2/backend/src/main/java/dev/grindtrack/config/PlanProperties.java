package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code grindtrack.plan.*} block.
 *
 * @param eveningCron when the phone gets where the plan stands — the week's hours against the
 *     target, the next target and how far off it is, tomorrow's first block. Sent only once a plan
 *     is imported.
 */
@ConfigurationProperties(prefix = "grindtrack.plan")
public record PlanProperties(String eveningCron) {}
