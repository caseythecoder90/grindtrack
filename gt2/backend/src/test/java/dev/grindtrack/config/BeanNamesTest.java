package dev.grindtrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

/**
 * Two components with the same simple name in different packages get the same bean name, and the
 * context refuses to start — which no unit test sees, because none of them boots the whole app. The
 * chat's first deploy found {@code chat.api.ChatController} beside {@code
 * assistant.api.ChatController} that way. This walks every stereotyped class the way the container
 * would and says so before a merge.
 */
class BeanNamesTest {

  @Test
  void everyComponentHasABeanNameOfItsOwn() {
    var scanner = new ClassPathScanningCandidateComponentProvider(true);
    var namer = AnnotationBeanNameGenerator.INSTANCE;
    Map<String, List<String>> byName = new HashMap<>();
    for (BeanDefinition bd : scanner.findCandidateComponents("dev.grindtrack")) {
      String name = namer.generateBeanName(bd, null);
      byName.computeIfAbsent(name, n -> new ArrayList<>()).add(bd.getBeanClassName());
    }
    List<String> clashes =
        byName.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> e.getKey() + " ← " + e.getValue())
            .sorted()
            .toList();
    assertThat(clashes).as("bean names claimed by more than one class").isEmpty();
    assertThat(byName).containsKey("roomController").containsKey("roomService");
  }
}
