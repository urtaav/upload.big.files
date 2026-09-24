package com.videoflow.upload.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible storage connection settings, all sourced from the environment.
 * The application only ever reads the values; adapters that need a different
 * configuration stay behind this record.
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(String endpoint, String region, String accessKey, String secretKey,
                                  String bucket, boolean pathStyleAccess) {
}