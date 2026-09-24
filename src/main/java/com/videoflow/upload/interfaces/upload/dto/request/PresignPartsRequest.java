package com.videoflow.upload.interfaces.upload.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "Part numbers for which presigned upload URLs are requested.")
public record PresignPartsRequest(

        @Schema(example = "[1,2,3,4,5]")
        @NotEmpty(message = "at least one partNumber is required")
        @Size(max = 10000, message = "too many part numbers in a single request")
        Set<@Min(value = 1, message = "partNumber must be >= 1") Integer> partNumbers
) {
}