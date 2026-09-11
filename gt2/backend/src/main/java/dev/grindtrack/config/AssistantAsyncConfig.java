package dev.grindtrack.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The threads a streamed assistant turn runs on.
 *
 * <p>Its own pool rather than the container's, because these tasks are unlike every other request
 * the app serves: one of them occupies a thread for thirty seconds doing nothing but waiting on a
 * socket. Borrowing Tomcat's threads for that would let a handful of chat turns starve the rest of
 * the app of the threads it needs to answer in milliseconds.
 *
 * <p>Sized for one person, which is who uses this. Two turns at once is already more than a person
 * can read; the third waits in a short queue, and past that the caller is told so immediately
 * rather than joining a queue it will time out in.
 */
@Configuration
public class AssistantAsyncConfig {

  @Bean
  public Executor assistantTurnExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(4);
    executor.setThreadNamePrefix("assistant-turn-");
    // Let a turn in flight finish rather than cutting the stream off mid-answer on a redeploy.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
