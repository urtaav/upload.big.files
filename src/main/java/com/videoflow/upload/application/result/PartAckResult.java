package com.videoflow.upload.application.result;

import java.util.UUID;

/**
 * Result of acknowledging one uploaded part.
 */
public record PartAckResult(UUID uploadId, int partNumber, String etag, long uploadedParts) {
}