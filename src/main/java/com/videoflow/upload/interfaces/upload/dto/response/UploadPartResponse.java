package com.videoflow.upload.interfaces.upload.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One part already acknowledged for an upload session.
 *
 * <p>The ETag is echoed back so a client that lost its local state (page
 * reload, another device) can resume: it re-uploads only the missing parts and
 * still has every ETag required by the completion request.
 */
@Schema(description = "A part already acknowledged by the API.")
public record UploadPartResponse(

        @Schema(example = "1")
        int partNumber,

        @Schema(example = "\"9fae43f1a6d5e1c9e88495f95800c340\"")
        String etag,

        @Schema(description = "Part size in bytes as reported on acknowledgment", example = "10485760")
        long size,

        Instant uploadedAt) {
}
