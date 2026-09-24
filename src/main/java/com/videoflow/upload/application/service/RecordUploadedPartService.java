package com.videoflow.upload.application.service;

import com.videoflow.upload.application.result.PartAckResult;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.UploadRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Registers a part that the client uploaded directly to object storage.
 *
 * <p>The registration is guarded by a compare-and-set that only accepts parts
 * while the upload is in {@code CREATED} or {@code UPLOADING} state, so a
 * racing complete/cancel cannot observe a part recorded "after" the fact.
 */
@Service
public class RecordUploadedPartService {

    private final UploadRepository uploadRepository;
    private final UploadAccessGuard accessGuard;

    public RecordUploadedPartService(UploadRepository uploadRepository, UploadAccessGuard accessGuard) {
        this.uploadRepository = uploadRepository;
        this.accessGuard = accessGuard;
    }

    public PartAckResult ack(UUID userId, UUID uploadId, int partNumber, String etag, long size) {
        Upload upload = accessGuard.requireOwned(uploadId, userId);
        accessGuard.requireNotExpired(upload);

        if (!upload.partNumberInRange(partNumber)) {
            throw new UploadException(ErrorCode.INVALID_PART,
                    "Part " + partNumber + " is out of range (1.." + upload.getTotalParts() + ").");
        }

        boolean recorded = uploadRepository.saveUploadedPartIfOpen(uploadId, partNumber, etag, size);
        if (!recorded) {
            Upload current = uploadRepository.findById(uploadId).orElse(upload);
            if (current.getStatus() == UploadStatus.COMPLETED) {
                throw new UploadException(ErrorCode.UPLOAD_ALREADY_COMPLETED, "The upload is already completed.");
            }
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "Cannot register parts while the upload is in state " + current.getStatus() + ".");
        }
        return new PartAckResult(uploadId, partNumber, etag, uploadRepository.countUploadedParts(uploadId));
    }
}