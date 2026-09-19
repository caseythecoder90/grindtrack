package dev.grindtrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

/**
 * With the app's own executors in the context, Boot does not provide a {@link TaskScheduler}; the
 * one in {@link SchedulingConfig} has to be there, because {@code ChatService} needs it and the
 * {@code @Scheduled} jobs are better on it than on a fallback thread.
 */
class SchedulingConfigTest {

  @Test
  void thereIsATaskSchedulerEvenWithTheAppsOwnExecutorsAround() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
        .withUserConfiguration(
            SchedulingConfig.class, SpeechAsyncConfig.class, AssistantAsyncConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(TaskScheduler.class);
              assertThat(context).hasBean("taskScheduler");
              assertThat(context).hasBean("speechScheduler");
            });
  }
}
