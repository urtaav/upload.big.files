package com.videoflow.upload.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Temporary identity filter used until JWT authentication is introduced.
 *
 * <p>It reads the caller identity from the configured header (default
 * {@code X-User-Id}) and stores it as a request attribute that controllers
 * read through {@link RequestAttributes#CURRENT_USER_ID}. When no header value
 * is provided and a local default is configured, the default is used.
 *
 * <p>This mechanism MUST be replaced by JWT authentication before any
 * non-local deployment.
 */
@Component
public class HeaderUserAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;

    public HeaderUserAuthenticationFilter(SecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * CORS preflight requests never carry the identity header, so they must not
     * be rejected here. Letting them through lets the CORS filter answer them.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        UUID userId = headerUserId(request);
        if (userId == null && properties.hasDefaultUserId()) {
            userId = parseUuid(properties.defaultUserId());
        }
        if (userId == null) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(RequestAttributes.CURRENT_USER_ID, userId);
        filterChain.doFilter(request, response);
    }

    private UUID headerUserId(HttpServletRequest request) {
        String raw = request.getHeader(properties.effectiveHeaderName());
        return parseUuid(raw);
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                java.util.Map.of("code", "UNAUTHORIZED", "message", "Missing or invalid user identity."));
    }
}