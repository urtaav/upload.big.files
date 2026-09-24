package com.videoflow.upload.application.result;

import java.time.Instant;

/**
 * A presigned URL for one part, plus its expiry.
 */
public record PresignedPart(int partNumber, String uploadUrl, Instant expiresAt) {
}