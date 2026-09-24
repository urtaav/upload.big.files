package com.videoflow.upload.domain.model;

import java.time.Instant;

/**
 * A single uploaded part of a multipart upload, identified by part number and
 * tracked by its storage ETag.
 */
public record UploadPart(int partNumber, String etag, long size, Instant uploadedAt) {

    public UploadPart {
        if (partNumber < 1) {
            throw new IllegalArgumentException("partNumber must be >= 1");
        }
        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("etag must not be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        if (uploadedAt == null) {
            throw new IllegalArgumentException("uploadedAt must not be null");
        }
    }
}