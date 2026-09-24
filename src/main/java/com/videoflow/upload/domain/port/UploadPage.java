package com.videoflow.upload.domain.port;

import com.videoflow.upload.domain.model.Upload;
import java.util.List;

/**
 * One page of upload sessions.
 *
 * <p>Deliberately not Spring Data's {@code Page}: paging is a concept the
 * application layer needs, the framework that implements it is not.
 *
 * @param items          the sessions on this page, newest first
 * @param uploadedCounts acknowledged part count per upload, resolved in one
 *                       query so a listing never degrades into N+1
 */
public record UploadPage(List<Upload> items,
                         java.util.Map<java.util.UUID, Long> uploadedCounts,
                         int page,
                         int size,
                         long totalElements) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public long uploadedPartsOf(java.util.UUID uploadId) {
        return uploadedCounts == null ? 0L : uploadedCounts.getOrDefault(uploadId, 0L);
    }
}
