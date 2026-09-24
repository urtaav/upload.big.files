package com.videoflow.upload.interfaces.upload.dto.response;

import com.videoflow.upload.domain.model.UploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "State of an upload session.")
public record UploadResponse(

        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID uploadId,

        String fileName,

        String contentType,

        long size,

        @Schema(example = "videos/3fa85f64-5717-4562-b3fc-2c963f66afa6/original.mp4")
        String objectKey,

        UploadStatus status,

        int partSize,

        int totalParts,

        long uploadedParts,

        @Schema(description = "Parts already acknowledged, ordered by part number. Lets a client resume "
                + "without any local state: it re-uploads only what is missing and still holds every ETag.")
        List<UploadPartResponse> parts,

        Instant createdAt,

        Instant updatedAt,

        Instant expiresAt
) {
}