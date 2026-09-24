package com.videoflow.upload.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-origin settings for browser clients.
 *
 * <p>The API is called directly from a browser SPA that lives on a different
 * origin during development (the Vite dev server). Without an explicit
 * allow-list every request from that origin is rejected by the browser before
 * it ever reaches a controller.
 *
 * <p>The list is empty by default so CORS stays disabled unless a deployment
 * opts in. Wildcards are intentionally not supported: credentials are not used,
 * but an unrestricted API surface is still not a sane default.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public boolean isEnabled() {
        return allowedOrigins != null && !allowedOrigins.isEmpty();
    }

    public List<String> effectiveAllowedOrigins() {
        return allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
