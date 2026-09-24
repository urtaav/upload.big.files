package com.videoflow.upload.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.application.result.CreateUploadResult;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.port.StoragePort;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class CreateUploadServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final long PART_SIZE = 10 * 1024 * 1024L;

    private StoragePort storagePort;
    private UploadRepository repository;
    private UploadSettings settings;
    private CreateUploadService service;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);
        repository = mock(UploadRepository.class);
        settings = new UploadSettings(500L * 1024 * 1024 * 1024, (int) PART_SIZE,
                List.of("video/mp4", "video/quicktime"), Duration.ofMinutes(15), Duration.ofHours(24));
        service = new CreateUploadService(storagePort, repository, settings);
    }

    @Test
    void createsSessionAndStartsMultipartUpload() {
        when(storagePort.createMultipartUpload(anyString(), anyString())).thenReturn("storage-upload-1");

        CreateUploadResult result = service.create(USER, null, "clip.mp4", "video/mp4", 26 * 1024 * 1024L);

        assertTrue(result.created());
        assertEquals(3, result.snapshot().upload().getTotalParts());
        assertEquals(26L * 1024 * 1024, result.snapshot().upload().getSize());
        verify(storagePort).createMultipartUpload(anyString(), eq("video/mp4"));

        ArgumentCaptor<Upload> saved = ArgumentCaptor.forClass(Upload.class);
        verify(repository).save(saved.capture());
        assertTrue(saved.getValue().getObjectKey().startsWith("uploads/"));
        assertTrue(saved.getValue().getObjectKey().endsWith("/clip.mp4"),
                "the key keeps the name the user recognises: " + saved.getValue().getObjectKey());
        assertEquals(USER, saved.getValue().getUserId());
    }

    @Test
    void wildcardAllowlistAcceptsAnyContentType() {
        UploadSettings openSettings = new UploadSettings(500L * 1024 * 1024 * 1024, (int) PART_SIZE,
                List.of("*"), Duration.ofMinutes(15), Duration.ofHours(24));
        CreateUploadService openService = new CreateUploadService(storagePort, repository, openSettings);
        when(storagePort.createMultipartUpload(anyString(), anyString())).thenReturn("s");

        CreateUploadResult result = openService.create(USER, null, "budget.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1024L);

        assertTrue(result.created());
        ArgumentCaptor<Upload> saved = ArgumentCaptor.forClass(Upload.class);
        verify(repository).save(saved.capture());
        assertTrue(saved.getValue().getObjectKey().endsWith("/budget.xlsx"));
    }

    @Test
    void hostileFileNameCannotEscapeItsPrefix() {
        UploadSettings openSettings = new UploadSettings(500L * 1024 * 1024 * 1024, (int) PART_SIZE,
                List.of("*"), Duration.ofMinutes(15), Duration.ofHours(24));
        CreateUploadService openService = new CreateUploadService(storagePort, repository, openSettings);
        when(storagePort.createMultipartUpload(anyString(), anyString())).thenReturn("s");

        openService.create(USER, null, "../../../etc/passwd", "text/plain", 10L);

        ArgumentCaptor<Upload> saved = ArgumentCaptor.forClass(Upload.class);
        verify(repository).save(saved.capture());
        String key = saved.getValue().getObjectKey();
        assertFalse(key.contains(".."), key);
        assertTrue(key.endsWith("/passwd"), key);
        // The name shown to the user is untouched; only the storage key is sanitised.
        assertEquals("../../../etc/passwd", saved.getValue().getFileName());
    }

    @Test
    void singlePartUploadAlwaysHasAtLeastOnePart() {
        when(storagePort.createMultipartUpload(anyString(), anyString())).thenReturn("s");
        CreateUploadResult result = service.create(USER, null, "clip.mp4", "video/mp4", 1024L);
        assertEquals(1, result.snapshot().upload().getTotalParts());
    }

    @Test
    void idempotencyReplayReturnsExistingWithoutStartingNewUpload() {
        Upload existing = upload("existing-key");
        when(repository.findByIdempotencyKey(USER, "existing-key")).thenReturn(Optional.of(existing));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findUploadedParts(existing.getId())).thenReturn(List.of());

        CreateUploadResult result = service.create(USER, "existing-key", "clip.mp4", "video/mp4", 1024L);

        assertFalse(result.created());
        assertEquals(existing.getId(), result.snapshot().upload().getId());
        verify(storagePort, never()).createMultipartUpload(anyString(), anyString());
    }

    @Test
    void concurrentDuplicateKeyFallsBackToWinner() {
        Upload existing = upload("dup-key");
        when(repository.findByIdempotencyKey(USER, "dup-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.findUploadedParts(existing.getId())).thenReturn(List.of());
        when(storagePort.createMultipartUpload(anyString(), anyString())).thenReturn("s");
        when(repository.save(any(Upload.class))).thenThrow(new DataIntegrityViolationException("unique"));

        CreateUploadResult result = service.create(USER, "dup-key", "clip.mp4", "video/mp4", 1024L);

        assertFalse(result.created());
        assertEquals(existing.getId(), result.snapshot().upload().getId());
    }

    @Test
    void rejectsUnsupportedContentType() {
        UploadException ex = assertThrows(UploadException.class,
                () -> service.create(USER, null, "clip.txt", "text/plain", 1024L));
        assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());
    }

    @Test
    void rejectsFileLargerThanLimit() {
        UploadException ex = assertThrows(UploadException.class,
                () -> service.create(USER, null, "clip.mp4", "video/mp4", 1024L * 1024 * 1024 * 1024));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
        verify(storagePort, never()).createMultipartUpload(anyString(), anyString());
    }

    @Test
    void rejectsUploadRequiringTooManyParts() {
        long size = 10_001L * PART_SIZE;
        UploadException ex = assertThrows(UploadException.class,
                () -> service.create(USER, null, "clip.mp4", "video/mp4", size));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        verify(storagePort, never()).createMultipartUpload(anyString(), anyString());
    }

    @Test
    void wrapsStorageFailureAsStorageError() {
        when(storagePort.createMultipartUpload(anyString(), anyString()))
                .thenThrow(new IllegalStateException("minio down"));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.create(USER, null, "clip.mp4", "video/mp4", 1024L));
        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    private Upload upload(String idempotencyKey) {
        return Upload.createCreated(USER, idempotencyKey, "clip.mp4", "video/mp4", 1024L,
                "videos/x/original.mp4", "storage-upload", (int) PART_SIZE, 1,
                java.time.Instant.now().plus(Duration.ofHours(24)),
                java.time.Instant.now().minusSeconds(1));
    }
}