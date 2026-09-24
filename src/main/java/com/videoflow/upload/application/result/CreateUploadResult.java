package com.videoflow.upload.application.result;

/**
 * Result of {@code POST /v1/uploads}. When the same idempotency key caused a
 * replay, {@code created} is {@code false} and the caller should answer with
 * HTTP 200 instead of 201.
 */
public record CreateUploadResult(UploadSnapshot snapshot, boolean created) {
}