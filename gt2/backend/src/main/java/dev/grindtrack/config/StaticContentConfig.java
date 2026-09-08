package dev.grindtrack.config;

import org.apache.catalina.startup.Tomcat;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Serving rules for the built UI, which ships inside the jar under {@code static/}. */
@Configuration
public class StaticContentConfig {

  /**
   * Teaches Tomcat the {@code .webmanifest} extension.
   *
   * <p>{@link Tomcat}'s default mappings predate the type, so the web app manifest would otherwise
   * be served as {@code application/octet-stream}. Browsers are inconsistent about accepting a
   * manifest with the wrong content type, and when one is rejected the failure is silent — the
   * install prompt simply never appears.
   */
  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webManifestMimeType() {
    return factory -> {
      MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
      mappings.add("webmanifest", "application/manifest+json");
      factory.setMimeMappings(mappings);
    };
  }
}
