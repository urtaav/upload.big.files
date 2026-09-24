package com.videoflow.upload.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UploadAccessGuardTest {

    private UploadRepository repository;
    private UploadAccessGuard guard;
    private UUID uploadId;
    private UUID owner;

    @BeforeEach
    void setUp() {
        repository = mock(UploadRepository.class);
        guard = new UploadAccessGuard(repository);
        uploadId = UUID.randomUUID();
        owner = UUID.randomUUID();
    }

    private Upload upload(Instant expiresAt) {
        return Upload.createCreated(owner, null, "clip.mp4", "video/mp4", 1024, "videos/x/original.mp4",
                "storage-upload", 5_242_880, 1, expiresAt, Instant.now().minusSeconds(1));
    }

    @Test
    void requireOwnedReturnsUploadWhenItBelongsToTheUser() {
        Upload upload = upload(Instant.now().plusSeconds(3600));
        when(repository.findById(uploadId)).thenReturn(Optional.of(upload));
        assertEquals(upload, guard.requireOwned(uploadId, owner));
    }

    @Test
    void requireOwnedThrowsNotFoundWhenMissing() {
        when(repository.findById(uploadId)).thenReturn(Optional.empty());
        UploadException ex = assertThrows(UploadException.class, () -> guard.requireOwned(uploadId, owner));
        assertEquals(ErrorCode.UPLOAD_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void requireOwnedThrowsForbiddenForAnotherUser() {
        Upload upload = upload(Instant.now().plusSeconds(3600));
        when(repository.findById(uploadId)).thenReturn(Optional.of(upload));
        UploadException ex = assertThrows(UploadException.class,
                () -> guard.requireOwned(uploadId, UUID.randomUUID()));
        assertEquals(ErrorCode.UNAUTHORIZED_UPLOAD, ex.getErrorCode());
    }

    @Test
    void requireNotExpiredThrowsWhenSessionIsOver() {
        Upload expired = Upload.restored(uploadId, owner, null, "clip.mp4", "video/mp4", 1024,
                "videos/x/original.mp4", "storage-upload", UploadStatus.UPLOADING, 5_242_880, 1,
                Instant.now().minus(java.time.Duration.ofSeconds(10)),
                Instant.now().minus(java.time.Duration.ofHours(1)),
                Instant.now().minus(java.time.Duration.ofHours(1)), 0L, java.util.List.of());
        UploadException ex = assertThrows(UploadException.class, () -> guard.requireNotExpired(expired));
        assertEquals(ErrorCode.UPLOAD_EXPIRED, ex.getErrorCode());
    }

    @Test
    void requireNotExpiredAcceptsActiveSession() {
        Upload active = upload(Instant.now().plusSeconds(3600));
        guard.requireNotExpired(active);
    }

    @Test
    void restoredUploadPreservesStateAndAllowsTransitionTo() {
        Upload created = upload(Instant.now().plusSeconds(3600));
        Upload restored = Upload.restored(created.getId(), created.getUserId(), created.getIdempotencyKey(),
                created.getFileName(), created.getContentType(), created.getSize(), created.getObjectKey(),
                created.getStorageUploadId(), UploadStatus.CREATED, created.getPartSize(), created.getTotalParts(),
                created.getExpiresAt(), created.getCreatedAt(), created.getUpdatedAt(), created.getVersion(),
                created.getUploadedParts());
        restored.transitionTo(UploadStatus.UPLOADING);
        assertEquals(UploadStatus.UPLOADING, restored.getStatus());
    }
}