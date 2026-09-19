package dev.grindtrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Turns Spring's scheduler on, and provides the scheduler. Its own class rather than an annotation
 * on the application so the first scheduled job in the app has somewhere to say this: the
 * deployment runs a single replica, so there is deliberately no leader election or distributed lock
 * around {@code @Scheduled} — revisit the moment replicas: 2 happens.
 *
 * <p>The bean is here on purpose. Boot only auto-configures a {@link TaskScheduler} when the
 * context has no {@code ScheduledExecutorService} of its own, and this app has two (the speech
 * relay's timer and the assistant's heartbeat), so without this bean there was none: the
 * {@code @Scheduled} jobs ran on a fallback single thread and {@code ChatService}, which schedules
 * the push that follows an unacknowledged message, could not be built — the first boot after the
 * chat merged found that out. Two threads, so a slow push does not hold the next job.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

  @Bean
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("sched-");
    scheduler.setDaemon(true);
    return scheduler;
  }
}
