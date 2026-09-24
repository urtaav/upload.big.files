package com.videoflow.upload.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pageable result for the upload list query, with the number of acknowledged
 * parts per upload (avoiding a query per row in the interface layer).
 */
public record UploadsPage(List<Upload> uploads,
                          Map<UUID, Long> uploadedPartsByUploadId,
                          long totalElements) {

    public long uploadedParts(UUID uploadId) {
        return uploadedPartsByUploadId.getOrDefault(uploadId, 0L);
    }
}