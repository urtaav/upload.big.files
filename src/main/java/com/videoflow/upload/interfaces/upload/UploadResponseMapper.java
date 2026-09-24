package com.videoflow.upload.interfaces.upload;

import com.videoflow.upload.application.result.CreateUploadResult;
import com.videoflow.upload.application.result.DownloadLink;
import com.videoflow.upload.application.result.PartSubmit;
import com.videoflow.upload.application.result.UploadSnapshot;
import com.videoflow.upload.interfaces.upload.dto.response.DownloadUrlResponse;
import com.videoflow.upload.interfaces.upload.dto.response.UploadListResponse;
import com.videoflow.upload.interfaces.upload.dto.response.UploadPartResponse;
import com.videoflow.upload.interfaces.upload.dto.response.UploadSummaryResponse;
import com.videoflow.upload.interfaces.upload.dto.response.UploadResponse;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.UploadPage;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Maps application results into HTTP response DTOs so JPA entities (or the
 * domain aggregate) are never exposed over the wire.
 */
@Component
public class UploadResponseMapper {

    /** States in which the object exists as a whole in storage. */
    private static final Set<UploadStatus> DOWNLOADABLE =
            Set.of(UploadStatus.COMPLETED, UploadStatus.PROCESSING, UploadStatus.READY);

    /** States in which the session still accepts parts. */
    private static final Set<UploadStatus> RESUMABLE =
            Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING);

    public UploadResponse toResponse(UploadSnapshot snapshot) {
        Upload upload = snapshot.upload();
        return new UploadResponse(
                upload.getId(),
                upload.getFileName(),
                upload.getContentType(),
                upload.getSize(),
                upload.getObjectKey(),
                upload.getStatus(),
                upload.getPartSize(),
                upload.getTotalParts(),
                snapshot.uploadedPartsCount(),
                parts(snapshot),
                upload.getCreatedAt(),
                upload.getUpdatedAt(),
                upload.getExpiresAt());
    }

    public UploadResponse toResponse(CreateUploadResult result) {
        return toResponse(result.snapshot());
    }

    public DownloadUrlResponse toResponse(DownloadLink link) {
        return new DownloadUrlResponse(
                link.uploadId(),
                link.fileName(),
                link.contentType(),
                link.size(),
                link.url(),
                link.expiresAt());
    }

    public UploadListResponse toResponse(UploadPage page) {
        List<UploadSummaryResponse> items = page.items().stream()
                .map(upload -> toSummary(upload, page.uploadedPartsOf(upload.getId())))
                .toList();
        return new UploadListResponse(items, page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private UploadSummaryResponse toSummary(Upload upload, long uploadedParts) {
        return new UploadSummaryResponse(
                upload.getId(),
                upload.getFileName(),
                upload.getContentType(),
                upload.getSize(),
                upload.getObjectKey(),
                upload.getStatus(),
                upload.getTotalParts(),
                uploadedParts,
                DOWNLOADABLE.contains(upload.getStatus()),
                RESUMABLE.contains(upload.getStatus()),
                upload.getCreatedAt(),
                upload.getUpdatedAt(),
                upload.getExpiresAt());
    }

    /**
     * Acknowledged parts ordered by part number. A client that lost its local
     * state asks for presigned URLs only for the parts that are missing, and
     * still holds every ETag the completion request requires.
     */
    private List<UploadPartResponse> parts(UploadSnapshot snapshot) {
        if (snapshot.uploadedParts() == null) {
            return List.of();
        }
        return snapshot.uploadedParts().stream()
                .sorted(Comparator.comparingInt(UploadPart::partNumber))
                .map(p -> new UploadPartResponse(p.partNumber(), p.etag(), p.size(), p.uploadedAt()))
                .toList();
    }
}