package com.videoflow.upload.infrastructure.config;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.infrastructure.storage.S3StorageProperties;
import org.springframework.stereotype.Component;

/**
 * Fail-fast validation of configuration that would otherwise break at the
 * first request (e.g. a part size below the 5 MiB S3 minimum for multipart).
 */
@Component
public class UploadConfigurationValidator {

    private static final int S3_MIN_PART_SIZE_BYTES = 5 * 1024 * 1024;

    public UploadConfigurationValidator(UploadSettings settings, S3StorageProperties storage) {
        if (settings.maxSizeBytes() <= 0) {
            throw new IllegalStateException("app.upload.max-size-bytes must be > 0");
        }
        if (settings.partSizeBytes() < S3_MIN_PART_SIZE_BYTES) {
            throw new IllegalStateException(
                    "app.upload.part-size-bytes must be >= " + S3_MIN_PART_SIZE_BYTES + " (S3 multipart minimum)");
        }
        if (settings.allowedContentTypes() == null || settings.allowedContentTypes().isEmpty()) {
            throw new IllegalStateException("app.upload.allowed-content-types must contain at least one MIME type");
        }
        if (storage.endpoint() == null || storage.endpoint().isBlank()) {
            throw new IllegalStateException("storage.s3.endpoint is required (MINIO_ENDPOINT)");
        }
        if (storage.bucket() == null || storage.bucket().isBlank()) {
            throw new IllegalStateException("storage.s3.bucket is required (MINIO_BUCKET)");
        }
        if (storage.accessKey() == null || storage.accessKey().isBlank()) {
            throw new IllegalStateException("storage.s3.access-key is required (MINIO_ACCESS_KEY)");
        }
        if (storage.secretKey() == null || storage.secretKey().isBlank()) {
            throw new IllegalStateException("storage.s3.secret-key is required (MINIO_SECRET_KEY)");
        }
    }
}