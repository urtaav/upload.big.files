package com.videoflow.upload.infrastructure.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Builds the CORS policy consumed by the security filter chain.
 *
 * <p>Credentials are disabled on purpose: identity travels in the
 * {@code X-User-Id} header (and later in an {@code Authorization} bearer
 * token), never in cookies.
 */
@Configuration
public class CorsConfig {

    private final CorsProperties properties;
    private final SecurityProperties securityProperties;

    public CorsConfig(CorsProperties properties, SecurityProperties securityProperties) {
        this.properties = properties;
        this.securityProperties = securityProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.effectiveAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "Idempotency-Key",
                securityProperties.effectiveHeaderName()));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.isEnabled()) {
            source.registerCorsConfiguration("/**", configuration);
        }
        return source;
    }
}
