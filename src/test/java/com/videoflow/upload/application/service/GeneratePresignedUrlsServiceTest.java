package com.videoflow.upload.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.application.result.PresignedPart;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.StoragePort;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratePresignedUrlsServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID UPLOAD_ID = UUID.randomUUID();
    private static final UploadSettings SETTINGS = new UploadSettings(500L * 1024 * 1024 * 1024,
            10 * 1024 * 1024, List.of("video/mp4", "video/quicktime"),
            Duration.ofMinutes(15), Duration.ofHours(24));

    private UploadRepository repository;
    private StoragePort storagePort;
    private UploadAccessGuard guard;
    private GeneratePresignedUrlsService service;

    @BeforeEach
    void setUp() {
        repository = mock(UploadRepository.class);
        storagePort = mock(StoragePort.class);
        guard = new UploadAccessGuard(repository);
        service = new GeneratePresignedUrlsService(repository, storagePort, SETTINGS, guard);
    }

    private Upload uploadWithStatus(UploadStatus status) {
        return Upload.restored(UPLOAD_ID, USER, null, "clip.mp4", "video/mp4", 26L * 1024 * 1024,
                "videos/" + UPLOAD_ID + "/original.mp4", "storage-upload", status, 10 * 1024 * 1024, 3,
                Instant.now().plus(Duration.ofHours(24)), Instant.now().minusSeconds(1),
                Instant.now(), 0L, List.of());
    }

    @Test
    void generatesUrlsSortedAndCallsStoragePerPart() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.CREATED)));
        when(storagePort.presignUploadPart(anyString(), anyString(), anyInt(), any()))
                .thenAnswer(inv -> "https://minio/part-" + inv.getArgument(2));

        List<PresignedPart> results = service.generate(USER, UPLOAD_ID, Set.of(3, 1, 2));

        assertEquals(List.of(1, 2, 3), results.stream().map(PresignedPart::partNumber).toList());
        assertEquals("https://minio/part-2", results.get(1).uploadUrl());
        verify(storagePort, org.mockito.Mockito.times(3))
                .presignUploadPart(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void allowsUploadingState() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.UPLOADING)));
        when(storagePort.presignUploadPart(anyString(), anyString(), anyInt(), any())).thenReturn("url");
        assertEquals(2, service.generate(USER, UPLOAD_ID, Set.of(1, 2)).size());
    }

    @Test
    void rejectsCompletedUpload() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.COMPLETED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.generate(USER, UPLOAD_ID, Set.of(1)));
        assertEquals(ErrorCode.UPLOAD_ALREADY_COMPLETED, ex.getErrorCode());
    }

    @Test
    void rejectsClosedState() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.CANCELLED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.generate(USER, UPLOAD_ID, Set.of(1)));
        assertEquals(ErrorCode.INVALID_UPLOAD_STATE, ex.getErrorCode());
    }

    @Test
    void rejectsEmptyPartList() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.CREATED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.generate(USER, UPLOAD_ID, Set.of()));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
        verify(storagePort, never()).presignUploadPart(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void rejectsPartOutOfRange() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.CREATED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.generate(USER, UPLOAD_ID, Set.of(4)));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
    }

    @Test
    void rejectsUploadNotOwnedByUser() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadWithStatus(UploadStatus.CREATED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.generate(UUID.randomUUID(), UPLOAD_ID, Set.of(1)));
        assertEquals(ErrorCode.UNAUTHORIZED_UPLOAD, ex.getErrorCode());
    }
}