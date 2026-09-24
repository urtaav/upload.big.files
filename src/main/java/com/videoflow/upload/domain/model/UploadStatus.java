package com.videoflow.upload.domain.model;

/**
 * Lifecycle of an upload session.
 *
 * <p>Allowed transitions (validated here and enforced again through a
 * compare-and-set update in the persistence layer):
 * <pre>
 * CREATED   -> UPLOADING | CANCELLED | EXPIRED
 * UPLOADING -> COMPLETING | CANCELLED | EXPIRED
 * COMPLETING-> COMPLETED | FAILED
 * COMPLETED -> PROCESSING        (future: media processing pipeline)
 * PROCESSING-> READY | FAILED
 * </pre>
 */
public enum UploadStatus {

    CREATED,
    UPLOADING,
    COMPLETING,
    COMPLETED,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean canTransitionTo(UploadStatus target) {
        return switch (this) {
            case CREATED -> target == UPLOADING || target == CANCELLED || target == EXPIRED;
            case UPLOADING -> target == COMPLETING || target == CANCELLED || target == EXPIRED;
            case COMPLETING -> target == COMPLETED || target == FAILED;
            case COMPLETED -> target == PROCESSING;
            case PROCESSING -> target == READY || target == FAILED;
            case CANCELLED, EXPIRED, FAILED, READY -> false;
        };
    }
}