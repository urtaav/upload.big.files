package com.videoflow.upload.domain.model;

import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Upload session aggregate root. Pure domain object: it does not depend on
 * Spring, JPA or the storage SDK.
 */
public final class Upload {

    private final UUID id;
    private final UUID userId;
    private final String idempotencyKey;
    private final String fileName;
    private final String contentType;
    private final long size;
    private final String objectKey;
    private final String storageUploadId;
    private UploadStatus status;
    private final int partSize;
    private final int totalParts;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;
    private final List<UploadPart> uploadedParts;

    private Upload(UUID id, UUID userId, String idempotencyKey, String fileName, String contentType,
                   long size, String objectKey, String storageUploadId, UploadStatus status,
                   int partSize, int totalParts, Instant expiresAt, Instant createdAt, Instant updatedAt,
                   long version, List<UploadPart> uploadedParts) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.idempotencyKey = idempotencyKey;
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.size = size;
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.storageUploadId = Objects.requireNonNull(storageUploadId, "storageUploadId");
        this.status = Objects.requireNonNull(status, "status");
        this.partSize = partSize;
        this.totalParts = totalParts;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        this.uploadedParts = List.copyOf(uploadedParts);
    }

    /**
     * Factory for a brand new upload session in {@code CREATED} state, with a
     * generated id.
     */
    public static Upload createCreated(UUID userId, String idempotencyKey, String fileName, String contentType,
                                       long size, String objectKey, String storageUploadId,
                                       int partSize, int totalParts, Instant expiresAt, Instant now) {
        return createCreated(UUID.randomUUID(), userId, idempotencyKey, fileName, contentType, size, objectKey,
                storageUploadId, partSize, totalParts, expiresAt, now);
    }

    /**
     * Factory for a brand new upload session with a caller-chosen id.
     *
     * <p>The caller needs the id before the aggregate exists, because the
     * storage key is built around it and the multipart upload must be started
     * before the session can be persisted.
     */
    public static Upload createCreated(UUID id, UUID userId, String idempotencyKey, String fileName,
                                       String contentType, long size, String objectKey, String storageUploadId,
                                       int partSize, int totalParts, Instant expiresAt, Instant now) {
        Objects.requireNonNull(id, "id");
        if (Objects.requireNonNull(userId, "userId") == null) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "userId must not be null");
        }
        if (Objects.requireNonNull(fileName, "fileName").isBlank()) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "fileName must not be blank");
        }
        if (Objects.requireNonNull(contentType, "contentType").isBlank()) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "contentType must not be blank");
        }
        if (size <= 0) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "size must be > 0");
        }
        if (partSize <= 0) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "partSize must be > 0");
        }
        if (totalParts < 1) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "totalParts must be >= 1");
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new UploadException(ErrorCode.INVALID_REQUEST, "expiresAt must be in the future");
        }
        return new Upload(id, userId, idempotencyKey, fileName, contentType, size, objectKey,
                storageUploadId, UploadStatus.CREATED, partSize, totalParts, expiresAt, now, now, 0L, List.of());
    }

    /**
     * Factory used by the persistence layer to rebuild an existing aggregate.
     */
    public static Upload restored(UUID id, UUID userId, String idempotencyKey, String fileName, String contentType,
                                  long size, String objectKey, String storageUploadId, UploadStatus status,
                                  int partSize, int totalParts, Instant expiresAt, Instant createdAt,
                                  Instant updatedAt, long version, List<UploadPart> uploadedParts) {
        return new Upload(id, userId, idempotencyKey, fileName, contentType, size, objectKey, storageUploadId,
                status, partSize, totalParts, expiresAt, createdAt, updatedAt, version, uploadedParts);
    }

    /**
     * Domain-level state transition guard. The authoritative guard for
     * concurrent flows is the compare-and-set update executed by the
     * repository.
     */
    public void transitionTo(UploadStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "Cannot transition from " + status + " to " + target + ".");
        }
        status = target;
        updatedAt = Instant.now();
    }

    public boolean isExpiredAt(Instant at) {
        return !at.isBefore(expiresAt);
    }

    public boolean partNumberInRange(int partNumber) {
        return partNumber >= 1 && partNumber <= totalParts;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getStorageUploadId() {
        return storageUploadId;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public int getPartSize() {
        return partSize;
    }

    public int getTotalParts() {
        return totalParts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<UploadPart> getUploadedParts() {
        return uploadedParts;
    }
}