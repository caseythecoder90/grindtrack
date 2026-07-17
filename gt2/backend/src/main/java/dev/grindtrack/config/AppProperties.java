package dev.grindtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code grindtrack.*} configuration block. */
@ConfigurationProperties(prefix = "grindtrack")
public record AppProperties(
    String jwtSecret,
    int accessTokenMinutes,
    int refreshTokenDays,
    boolean cookieSecure,
    String bootstrapUsername,
    String bootstrapPassword) {}
