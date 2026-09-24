package com.videoflow.upload.application.service;

import com.videoflow.upload.application.result.UploadSnapshot;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.StoragePort;
import com.videoflow.upload.domain.port.UploadRepository;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cancels an upload: transitions CREATED/UPLOADING to CANCELLED with a
 * compare-and-set and aborts the multipart upload in storage so its buffered
 * parts are released.
 *
 * <p>Idempotent: cancelling an already CANCELLED upload returns the current
 * state. A cancel racing a complete loses deterministically through the CAS
 * guard (complete wins if it reaches COMPLETING first; cancel wins otherwise).
 */
@Service
public class CancelUploadService {

    private static final Logger log = LoggerFactory.getLogger(CancelUploadService.class);

    private static final Set<UploadStatus> CANCELLABLE = Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING);

    private final UploadRepository uploadRepository;
    private final StoragePort storagePort;
    private final UploadAccessGuard accessGuard;

    public CancelUploadService(UploadRepository uploadRepository, StoragePort storagePort,
                               UploadAccessGuard accessGuard) {
        this.uploadRepository = uploadRepository;
        this.storagePort = storagePort;
        this.accessGuard = accessGuard;
    }

    public UploadSnapshot cancel(UUID userId, UUID uploadId) {
        Upload upload = accessGuard.requireOwned(uploadId, userId);

        if (upload.getStatus() == UploadStatus.CANCELLED) {
            return snapshotOf(uploadId);
        }
        if (upload.getStatus() == UploadStatus.COMPLETED) {
            throw new UploadException(ErrorCode.UPLOAD_ALREADY_COMPLETED, "A completed upload cannot be cancelled.");
        }
        accessGuard.requireNotExpired(upload);
        if (!CANCELLABLE.contains(upload.getStatus())) {
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "Cannot cancel an upload in state " + upload.getStatus() + ".");
        }

        boolean cancelled = uploadRepository.compareAndSetStatus(uploadId, CANCELLABLE, UploadStatus.CANCELLED);
        if (!cancelled) {
            Upload current = uploadRepository.findById(uploadId).orElse(upload);
            if (current.getStatus() == UploadStatus.CANCELLED) {
                return snapshotOf(uploadId);
            }
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "The upload changed state concurrently: " + current.getStatus() + ".");
        }

        abortStorage(upload);
        return snapshotOf(uploadId);
    }

    private void abortStorage(Upload upload) {
        try {
            storagePort.abortMultipartUpload(upload.getObjectKey(), upload.getStorageUploadId());
        } catch (RuntimeException ex) {
            // Best effort: a janitor will eventually clean orphaned multipart uploads.
            log.warn("Could not abort multipart upload {}; a janitor will clean it up.", upload.getId(), ex);
        }
    }

    private UploadSnapshot snapshotOf(UUID uploadId) {
        Upload current = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new UploadException(ErrorCode.UPLOAD_NOT_FOUND, "Upload not found: " + uploadId));
        return new UploadSnapshot(current, uploadRepository.findUploadedParts(uploadId));
    }
}