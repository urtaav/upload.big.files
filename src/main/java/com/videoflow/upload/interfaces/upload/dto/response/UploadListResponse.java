package com.videoflow.upload.interfaces.upload.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of upload sessions owned by the caller, newest first.")
public record UploadListResponse(

        List<UploadSummaryResponse> items,

        int page,

        int size,

        long totalElements,

        int totalPages) {
}
