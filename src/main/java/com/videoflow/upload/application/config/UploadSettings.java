package com.videoflow.upload.application.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upload orchestration business rules. Values come from the environment so no
 * size, part size, content-type allowlist or TTL is hardcoded.
 *
 * @param maxSizeBytes        absolute maximum accepted file size in bytes
 * @param partSizeBytes       fixed part size in bytes
 * @param allowedContentTypes allowlist of accepted MIME types
 * @param presignedUrlTtl     TTL of each presigned part URL
 * @param sessionTtl          how long an upload session stays open before EXPIRED
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadSettings(long maxSizeBytes,
                             int partSizeBytes,
                             List<String> allowedContentTypes,
                             Duration presignedUrlTtl,
                             Duration sessionTtl) {

    public int totalParts(long size) {
        long parts = (size + partSizeBytes - 1) / partSizeBytes;
        return (int) Math.max(1L, parts);
    }

    public boolean isAllowedContentType(String contentType) {
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            return false;
        }
        if (allowedContentTypes.contains("*")) {
            return true;
        }
        return allowedContentTypes.contains(contentType);
    }
}