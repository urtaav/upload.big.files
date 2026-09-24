package com.videoflow.upload.interfaces.upload.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Acknowledgment that a part was uploaded directly to storage.")
public record AckPartRequest(

        @Schema(description = "ETag returned by storage in the part upload response", example = "\"9fae43f1a6d5e1c9e88495f95800c340\"")
        @NotBlank(message = "etag is required")
        @Size(max = 128, message = "etag must be at most 128 characters")
        String etag,

        @Schema(description = "Part size in bytes", example = "10485760")
        @NotNull(message = "size is required")
        @PositiveOrZero(message = "size must be zero or positive")
        Long size
) {
}