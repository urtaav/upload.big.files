package com.videoflow.upload.interfaces.upload.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Result of acknowledging one uploaded part.")
public record PartAckResponse(UUID uploadId, int partNumber, String etag, long uploadedParts) {
}