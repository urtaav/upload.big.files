package com.videoflow.upload.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings for the pre-JWT identity mechanism.
 *
 * <p>Until JWT authentication is implemented, the API identifies the caller
 * through a configurable HTTP header. The configured default user id is a
 * convenience for local development only and must remain empty in production.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String headerName, String defaultUserId) {

    public String effectiveHeaderName() {
        return headerName == null || headerName.isBlank() ? "X-User-Id" : headerName;
    }

    public boolean hasDefaultUserId() {
        return defaultUserId != null && !defaultUserId.isBlank();
    }
}