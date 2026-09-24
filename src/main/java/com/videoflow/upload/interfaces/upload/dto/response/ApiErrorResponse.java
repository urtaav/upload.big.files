package com.videoflow.upload.interfaces.upload.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Uniform error body returned by the API.")
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId) {

    public static ApiErrorResponse of(int status, String code, String message, String path, String traceId) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, traceId);
    }
}