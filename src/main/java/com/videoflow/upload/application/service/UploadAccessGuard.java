package com.videoflow.upload.application.service;

import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared ownership and expiration guards used by the upload use cases.
 */
@Component
public class UploadAccessGuard {

    private final UploadRepository uploadRepository;

    public UploadAccessGuard(UploadRepository uploadRepository) {
        this.uploadRepository = uploadRepository;
    }

    /**
     * Loads an upload and verifies ownership. The 403 response handles the
     * case where the upload exists but belongs to a different user.
     */
    public Upload requireOwned(UUID uploadId, UUID userId) {
        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new UploadException(ErrorCode.UPLOAD_NOT_FOUND, "Upload not found: " + uploadId));
        if (!upload.getUserId().equals(userId)) {
            throw new UploadException(ErrorCode.UNAUTHORIZED_UPLOAD, "Access to upload " + uploadId + " is not authorized.");
        }
        return upload;
    }

    public void requireNotExpired(Upload upload) {
        if (upload.isExpiredAt(Instant.now())) {
            throw new UploadException(ErrorCode.UPLOAD_EXPIRED, "Upload expired at " + upload.getExpiresAt() + ".");
        }
    }
}