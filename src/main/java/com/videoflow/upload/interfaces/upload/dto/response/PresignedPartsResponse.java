package com.videoflow.upload.interfaces.upload.dto.response;

import com.videoflow.upload.application.result.PresignedPart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Presigned URLs that allow uploading parts directly to storage (bypassing the API).")
public record PresignedPartsResponse(UUID uploadId, List<PresignedPart> parts) {
}