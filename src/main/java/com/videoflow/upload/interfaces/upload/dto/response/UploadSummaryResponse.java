package com.videoflow.upload.interfaces.upload.dto.response;

import com.videoflow.upload.domain.model.UploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the upload listing. Deliberately lighter than
 * {@link UploadResponse}: a browser rendering a file explorer needs names,
 * sizes and progress, not the ETag of every part.
 */
@Schema(description = "An upload session as shown in a listing.")
public record UploadSummaryResponse(

        UUID uploadId,

        String fileName,

        String contentType,

        long size,

        String objectKey,

        UploadStatus status,

        int totalParts,

        long uploadedParts,

        @Schema(description = "True when the object is assembled and can be downloaded")
        boolean downloadable,

        @Schema(description = "True when the session still accepts parts, so an interrupted upload can be resumed")
        boolean resumable,

        Instant createdAt,

        Instant updatedAt,

        Instant expiresAt) {
}
