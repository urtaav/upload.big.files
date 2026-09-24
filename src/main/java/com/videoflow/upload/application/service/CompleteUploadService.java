package com.videoflow.upload.application.service;

import com.videoflow.upload.application.result.PartSubmit;
import com.videoflow.upload.application.result.UploadSnapshot;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.StoragePort;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Finalizes a multipart upload: validates the submitted part list, guards the
 * CREATED/UPLOADING -> COMPLETING transition with a compare-and-set, assembles
 * the object in storage and only then marks it COMPLETED.
 *
 * <p>Idempotent: completing an already COMPLETED upload returns the current
 * state instead of an error, so network retries are safe.
 */
@Service
public class CompleteUploadService {

    private static final Logger log = LoggerFactory.getLogger(CompleteUploadService.class);

    private static final Set<UploadStatus> COMPLETABLE = Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING);

    private final UploadRepository uploadRepository;
    private final StoragePort storagePort;
    private final UploadAccessGuard accessGuard;

    public CompleteUploadService(UploadRepository uploadRepository, StoragePort storagePort,
                                 UploadAccessGuard accessGuard) {
        this.uploadRepository = uploadRepository;
        this.storagePort = storagePort;
        this.accessGuard = accessGuard;
    }

    public UploadSnapshot complete(UUID userId, UUID uploadId, List<PartSubmit> submitted) {
        Upload upload = accessGuard.requireOwned(uploadId, userId);
        accessGuard.requireNotExpired(upload);

        UploadStatus status = upload.getStatus();
        if (status == UploadStatus.COMPLETED) {
            return snapshotOf(uploadId);
        }
        if (!COMPLETABLE.contains(status)) {
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "Cannot complete an upload in state " + status + ".");
        }

        List<PartSubmit> validated = validateCompleteList(submitted, upload.getTotalParts());
        verifyEtagsAgainstAcks(uploadId, validated);

        boolean locked = uploadRepository.compareAndSetStatus(uploadId, COMPLETABLE, UploadStatus.COMPLETING);
        if (!locked) {
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "The upload is being completed by another request or is no longer active.");
        }

        try {
            List<StoragePort.CompletedPart> completedParts = validated.stream()
                    .map(p -> new StoragePort.CompletedPart(p.partNumber(), p.etag()))
                    .toList();
            storagePort.completeMultipartUpload(upload.getObjectKey(), upload.getStorageUploadId(), completedParts);
        } catch (RuntimeException ex) {
            uploadRepository.compareAndSetStatus(uploadId, Set.of(UploadStatus.COMPLETING), UploadStatus.FAILED);
            log.error("Failed to complete multipart upload {} in storage; marked as FAILED.", uploadId, ex);
            throw new UploadException(ErrorCode.STORAGE_ERROR, "Failed to finalize the multipart upload in storage.", ex);
        }

        uploadRepository.compareAndSetStatus(uploadId, Set.of(UploadStatus.COMPLETING), UploadStatus.COMPLETED);

        // Persist ETags the client never acked, so GET reflects the full part list.
        Instant now = Instant.now();
        List<UploadPart> parts = validated.stream()
                .map(p -> new UploadPart(p.partNumber(), p.etag(), 0L, now))
                .toList();
        uploadRepository.upsertUploadedParts(uploadId, parts);

        return snapshotOf(uploadId);
    }

    private List<PartSubmit> validateCompleteList(List<PartSubmit> submitted, int totalParts) {
        if (submitted == null || submitted.size() != totalParts) {
            throw new UploadException(ErrorCode.INVALID_PART,
                    "The completion request must contain exactly " + totalParts + " parts.");
        }
        Map<Integer, PartSubmit> byNumber = new HashMap<>();
        for (PartSubmit part : submitted) {
            if (part.partNumber() < 1 || part.partNumber() > totalParts) {
                throw new UploadException(ErrorCode.INVALID_PART,
                        "Part " + part.partNumber() + " is out of range (1.." + totalParts + ").");
            }
            if (part.etag() == null || part.etag().isBlank()) {
                throw new UploadException(ErrorCode.INVALID_PART,
                        "Part " + part.partNumber() + " is missing its ETag.");
            }
            if (byNumber.put(part.partNumber(), part) != null) {
                throw new UploadException(ErrorCode.INVALID_PART,
                        "Part " + part.partNumber() + " is duplicated in the request.");
            }
        }
        return java.util.stream.IntStream.rangeClosed(1, totalParts)
                .mapToObj(i -> byNumber.get(i))
                .toList();
    }

    private void verifyEtagsAgainstAcks(UUID uploadId, List<PartSubmit> validated) {
        Map<Integer, UploadPart> acked = new HashMap<>();
        for (UploadPart part : uploadRepository.findUploadedParts(uploadId)) {
            acked.put(part.partNumber(), part);
        }
        for (PartSubmit part : validated) {
            UploadPart ack = acked.get(part.partNumber());
            if (ack != null && !ack.etag().equals(part.etag())) {
                throw new UploadException(ErrorCode.INVALID_PART,
                        "ETag for part " + part.partNumber() + " does not match the acknowledged value.");
            }
        }
    }

    private UploadSnapshot snapshotOf(UUID uploadId) {
        Upload completed = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new UploadException(ErrorCode.UPLOAD_NOT_FOUND, "Upload not found: " + uploadId));
        return new UploadSnapshot(completed, uploadRepository.findUploadedParts(uploadId));
    }
}