package com.videoflow.upload.interfaces.upload.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Time-limited URL to download the finished object straight from storage.")
public record DownloadUrlResponse(

        UUID uploadId,

        @Schema(description = "Original file name; the download is served under this name",
                example = "vacaciones 2026.mp4")
        String fileName,

        String contentType,

        long size,

        @Schema(description = "Presigned GET URL. The API never streams the bytes itself.")
        String url,

        Instant expiresAt) {
}
