package com.videoflow.upload.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * A time-limited URL that lets a client download the finished object straight
 * from storage. As with uploads, the bytes never pass through the API.
 */
public record DownloadLink(UUID uploadId,
                           String fileName,
                           String contentType,
                           long size,
                           String url,
                           Instant expiresAt) {
}
