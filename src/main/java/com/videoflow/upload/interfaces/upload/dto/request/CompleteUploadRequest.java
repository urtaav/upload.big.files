package com.videoflow.upload.interfaces.upload.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Part list with ETags to finalize a multipart upload.")
public record CompleteUploadRequest(

        @Schema(description = "All parts (partNumber 1..N) with the ETag of each one")
        @NotEmpty(message = "parts must not be empty")
        List<@Valid CompletePart> parts
) {

    @Schema(name = "CompletePart", description = "One uploaded part")
    public record CompletePart(
            @NotNull(message = "partNumber is required")
            @Min(value = 1, message = "partNumber must be >= 1")
            Integer partNumber,

            @NotBlank(message = "etag is required")
            @Size(max = 128, message = "etag must be at most 128 characters")
            String etag
    ) {
    }
}