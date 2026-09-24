package com.videoflow.upload.interfaces.upload.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Metadata required to start a new upload session. The file bytes never travel through the API.")
public record CreateUploadRequest(

        @Schema(example = "video.mp4")
        @NotBlank(message = "fileName is required")
        @Size(max = 255, message = "fileName must be at most 255 characters")
        String fileName,

        @Schema(example = "video/mp4")
        @NotBlank(message = "contentType is required")
        @Size(max = 127, message = "contentType must be at most 127 characters")
        String contentType,

        @Schema(example = "524288000", description = "File size in bytes")
        @NotNull(message = "size is required")
        @Positive(message = "size must be positive")
        Long size
) {
}