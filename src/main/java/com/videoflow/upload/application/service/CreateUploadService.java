package com.videoflow.upload.application.service;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.application.result.CreateUploadResult;
import com.videoflow.upload.application.result.UploadSnapshot;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.ObjectKey;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.port.StoragePort;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Creates an upload session: validates metadata, starts the multipart upload
 * in object storage, persists the session and returns the blueprint (object
 * key, part size, total parts).
 *
 * <p>Idempotent through the {@code Idempotency-Key} header: a replay with the
 * same key returns the previously created session without starting a new
 * multipart upload.
 */
@Service
public class CreateUploadService {

    private static final int MAX_PARTS = 10_000;

    private final StoragePort storagePort;
    private final UploadRepository uploadRepository;
    private final UploadSettings settings;

    public CreateUploadService(StoragePort storagePort, UploadRepository uploadRepository, UploadSettings settings) {
        this.storagePort = storagePort;
        this.uploadRepository = uploadRepository;
        this.settings = settings;
    }

    public CreateUploadResult create(UUID userId, String idempotencyKey, String fileName, String contentType, long size) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = uploadRepository.findByIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return new CreateUploadResult(snapshotOf(existing.get().getId()), false);
            }
        }

        if (!settings.isAllowedContentType(contentType)) {
            throw new UploadException(ErrorCode.INVALID_FILE_TYPE,
                    "Content type '" + contentType + "' is not supported. Allowed: " + settings.allowedContentTypes());
        }
        if (size > settings.maxSizeBytes()) {
            throw new UploadException(ErrorCode.FILE_TOO_LARGE,
                    "File size " + size + " exceeds the maximum allowed " + settings.maxSizeBytes() + " bytes.");
        }

        int partSize = settings.partSizeBytes();
        int totalParts = computeTotalParts(size, partSize);

        UUID uploadId = UUID.randomUUID();
        String objectKey = ObjectKey.forUpload(uploadId, fileName);

        String storageUploadId = startMultipartUpload(objectKey, contentType);

        Instant now = Instant.now();
        Upload upload = Upload.createCreated(uploadId, userId, idempotencyKey, fileName, contentType, size,
                objectKey, storageUploadId, partSize, totalParts, now.plus(settings.sessionTtl()), now);
        try {
            uploadRepository.save(upload);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request with the same idempotency key: return the winner.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                var existing = uploadRepository.findByIdempotencyKey(userId, idempotencyKey);
                if (existing.isPresent()) {
                    return new CreateUploadResult(snapshotOf(existing.get().getId()), false);
                }
            }
            throw new UploadException(ErrorCode.INTERNAL_ERROR, "Could not persist the upload session.", ex);
        }
        return new CreateUploadResult(new UploadSnapshot(upload, List.of()), true);
    }

    private String startMultipartUpload(String objectKey, String contentType) {
        try {
            return storagePort.createMultipartUpload(objectKey, contentType);
        } catch (RuntimeException ex) {
            throw new UploadException(ErrorCode.STORAGE_ERROR, "Could not initialize the multipart upload.", ex);
        }
    }

    private int computeTotalParts(long size, int partSize) {
        int totalParts = settings.totalParts(size);
        if (totalParts > MAX_PARTS) {
            throw new UploadException(ErrorCode.INVALID_REQUEST,
                    "File is too large for the configured part size (would require more than " + MAX_PARTS + " parts).");
        }
        return totalParts;
    }

    private UploadSnapshot snapshotOf(UUID uploadId) {
        Upload existing = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new UploadException(ErrorCode.UPLOAD_NOT_FOUND, "Upload not found: " + uploadId));
        return new UploadSnapshot(existing, uploadRepository.findUploadedParts(uploadId));
    }
}