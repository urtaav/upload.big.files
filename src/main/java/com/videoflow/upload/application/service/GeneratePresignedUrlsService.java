package com.videoflow.upload.application.service;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.application.result.PresignedPart;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.StoragePort;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Generates presigned URLs that let the client PUT parts directly to object
 * storage, keeping Spring Boot out of the binary data path.
 */
@Service
public class GeneratePresignedUrlsService {

    private static final Set<UploadStatus> OPEN_STATES = Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING);

    private final UploadRepository uploadRepository;
    private final StoragePort storagePort;
    private final UploadSettings settings;
    private final UploadAccessGuard accessGuard;

    public GeneratePresignedUrlsService(UploadRepository uploadRepository, StoragePort storagePort,
                                        UploadSettings settings, UploadAccessGuard accessGuard) {
        this.uploadRepository = uploadRepository;
        this.storagePort = storagePort;
        this.settings = settings;
        this.accessGuard = accessGuard;
    }

    public List<PresignedPart> generate(UUID userId, UUID uploadId, Set<Integer> partNumbers) {
        Upload upload = accessGuard.requireOwned(uploadId, userId);
        accessGuard.requireNotExpired(upload);

        UploadStatus status = upload.getStatus();
        if (status == UploadStatus.COMPLETED) {
            throw new UploadException(ErrorCode.UPLOAD_ALREADY_COMPLETED, "Cannot request parts for a completed upload.");
        }
        if (!OPEN_STATES.contains(status)) {
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "Cannot request parts while the upload is in state " + status + ".");
        }
        if (partNumbers == null || partNumbers.isEmpty()) {
            throw new UploadException(ErrorCode.INVALID_PART, "At least one part number is required.");
        }

        Instant expiresAt = Instant.now().plus(settings.presignedUrlTtl());
        return partNumbers.stream()
                .sorted()
                .map(partNumber -> presign(upload, partNumber, expiresAt))
                .toList();
    }

    private PresignedPart presign(Upload upload, int partNumber, Instant expiresAt) {
        if (!upload.partNumberInRange(partNumber)) {
            throw new UploadException(ErrorCode.INVALID_PART,
                    "Part " + partNumber + " is out of range (1.." + upload.getTotalParts() + ").");
        }
        String url = storagePort.presignUploadPart(upload.getObjectKey(), upload.getStorageUploadId(),
                partNumber, settings.presignedUrlTtl());
        return new PresignedPart(partNumber, url, expiresAt);
    }
}