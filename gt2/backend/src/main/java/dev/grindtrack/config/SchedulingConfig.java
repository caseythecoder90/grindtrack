package dev.grindtrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns Spring's scheduler on. Its own class rather than an annotation on the application so the
 * first scheduled job in the app has somewhere to say this: the deployment runs a single replica,
 * so there is deliberately no leader election or distributed lock around {@code @Scheduled} —
 * revisit the moment replicas: 2 happens.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
