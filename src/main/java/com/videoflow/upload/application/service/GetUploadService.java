package com.videoflow.upload.application.service;

import com.videoflow.upload.application.result.UploadSnapshot;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads an upload session, transitioning it to {@code EXPIRED} (best effort)
 * when its session TTL has passed.
 */
@Service
public class GetUploadService {

    private static final Set<UploadStatus> NOT_TERMINAL = Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING);

    private final UploadRepository uploadRepository;
    private final UploadAccessGuard accessGuard;

    public GetUploadService(UploadRepository uploadRepository, UploadAccessGuard accessGuard) {
        this.uploadRepository = uploadRepository;
        this.accessGuard = accessGuard;
    }

    public UploadSnapshot get(UUID userId, UUID uploadId) {
        Upload upload = accessGuard.requireOwned(uploadId, userId);
        if (upload.isExpiredAt(Instant.now())) {
            uploadRepository.compareAndSetStatus(uploadId, NOT_TERMINAL, UploadStatus.EXPIRED);
            upload = uploadRepository.findById(uploadId).orElse(upload);
        }
        return new UploadSnapshot(upload, uploadRepository.findUploadedParts(uploadId));
    }
}