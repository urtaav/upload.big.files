package com.videoflow.upload.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.videoflow.upload.application.result.PartSubmit;
import com.videoflow.upload.application.result.UploadSnapshot;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CompleteUploadServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID UPLOAD_ID = UUID.randomUUID();
    private static final int TOTAL_PARTS = 2;

    private UploadRepository repository;
    private StoragePort storagePort;
    private UploadAccessGuard guard;
    private CompleteUploadService service;

    @BeforeEach
    void setUp() {
        repository = mock(UploadRepository.class);
        storagePort = mock(StoragePort.class);
        guard = new UploadAccessGuard(repository);
        service = new CompleteUploadService(repository, storagePort, guard);
    }

    private Upload uploadIn(UploadStatus status) {
        return Upload.restored(UPLOAD_ID, USER, null, "clip.mp4", "video/mp4", 26L * 1024 * 1024,
                "videos/" + UPLOAD_ID + "/original.mp4", "storage-upload", status, 10 * 1024 * 1024, TOTAL_PARTS,
                Instant.now().plus(Duration.ofHours(24)), Instant.now().minusSeconds(1),
                Instant.now(), 0L, List.of());
    }

    @Test
    void completesUploadAfterVerifyingEtags() {
        Upload upload = uploadIn(UploadStatus.UPLOADING);
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
        when(repository.findUploadedParts(UPLOAD_ID))
                .thenReturn(List.of(new UploadPart(1, "\"etag-1\"", 10L, Instant.now().minusSeconds(5))));
        when(repository.compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING),
                UploadStatus.COMPLETING)).thenReturn(true);
        when(repository.compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.COMPLETING), UploadStatus.COMPLETED))
                .thenReturn(true);

        List<PartSubmit> submitted = List.of(new PartSubmit(1, "\"etag-1\""), new PartSubmit(2, "\"etag-2\""));
        UploadSnapshot result = service.complete(USER, UPLOAD_ID, submitted);

        verify(repository).compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING),
                UploadStatus.COMPLETING);
        verify(repository).compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.COMPLETING), UploadStatus.COMPLETED);
        assertEquals(1, result.uploadedParts().size());

        ArgumentCaptor<List<StoragePort.CompletedPart>> parts = ArgumentCaptor.forClass(List.class);
        verify(storagePort).completeMultipartUpload(eq("videos/" + UPLOAD_ID + "/original.mp4"),
                eq("storage-upload"), parts.capture());
        assertEquals(2, parts.getValue().size());
        assertEquals(1, parts.getValue().get(0).partNumber());
        assertEquals("\"etag-1\"", parts.getValue().get(0).etag());

        ArgumentCaptor<List<UploadPart>> upserted = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertUploadedParts(eq(UPLOAD_ID), upserted.capture());
        assertEquals(2, upserted.getValue().size());
    }

    @Test
    void completingAlreadyCompletedUploadIsIdempotent() {
        Upload completed = uploadIn(UploadStatus.COMPLETED);
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(completed));
        when(repository.findUploadedParts(UPLOAD_ID))
                .thenReturn(List.of(new UploadPart(1, "\"e1\"", 10L, Instant.now()),
                        new UploadPart(2, "\"e2\"", 10L, Instant.now())));

        UploadSnapshot result = service.complete(USER, UPLOAD_ID, List.of());

        assertEquals(UploadStatus.COMPLETED, result.upload().getStatus());
        assertEquals(2, result.uploadedParts().size());
        verify(storagePort, never()).completeMultipartUpload(any(), any(), anyList());
        verify(repository, never()).compareAndSetStatus(any(UUID.class), any(), any());
    }

    @Test
    void rejectsMissingParts() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadIn(UploadStatus.CREATED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, List.of(new PartSubmit(1, "\"e1\""))));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
        verify(storagePort, never()).completeMultipartUpload(any(), any(), anyList());
    }

    @Test
    void rejectsOutOfRangePartNumber() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadIn(UploadStatus.CREATED)));
        List<PartSubmit> submitted = List.of(new PartSubmit(1, "\"e1\""), new PartSubmit(3, "\"e3\""));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, submitted));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
    }

    @Test
    void rejectsDuplicatePartNumber() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadIn(UploadStatus.CREATED)));
        List<PartSubmit> submitted = List.of(new PartSubmit(1, "\"e1\""), new PartSubmit(1, "\"e1b\""));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, submitted));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
    }

    @Test
    void rejectsBlankEtag() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadIn(UploadStatus.CREATED)));
        List<PartSubmit> submitted = List.of(new PartSubmit(1, " "), new PartSubmit(2, "\"e2\""));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, submitted));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
    }

    @Test
    void rejectsEtagMismatchAgainstAcknowledgedPart() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadIn(UploadStatus.UPLOADING)));
        when(repository.findUploadedParts(UPLOAD_ID))
                .thenReturn(List.of(new UploadPart(1, "\"acked-etag\"", 10L, Instant.now())));
        List<PartSubmit> submitted = List.of(new PartSubmit(1, "\"other-etag\""), new PartSubmit(2, "\"e2\""));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, submitted));
        assertEquals(ErrorCode.INVALID_PART, ex.getErrorCode());
        verify(storagePort, never()).completeMultipartUpload(any(), any(), anyList());
    }

    @Test
    void rejectsStateThatCannotBeCompleted() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(uploadIn(UploadStatus.CANCELLED)));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, List.of(new PartSubmit(1, "\"e1\""), new PartSubmit(2, "\"e2\""))));
        assertEquals(ErrorCode.INVALID_UPLOAD_STATE, ex.getErrorCode());
    }

    @Test
    void rejectedWhenCompareAndSetLosesRace() {
        Upload upload = uploadIn(UploadStatus.UPLOADING);
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
        when(repository.findUploadedParts(UPLOAD_ID)).thenReturn(List.of());
        when(repository.compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING),
                UploadStatus.COMPLETING)).thenReturn(false);

        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID,
                        List.of(new PartSubmit(1, "\"e1\""), new PartSubmit(2, "\"e2\""))));
        assertEquals(ErrorCode.INVALID_UPLOAD_STATE, ex.getErrorCode());
        verify(storagePort, never()).completeMultipartUpload(any(), any(), anyList());
    }

    @Test
    void marksFailedWhenStorageCompletesWithError() {
        Upload upload = uploadIn(UploadStatus.UPLOADING);
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(upload));
        when(repository.findUploadedParts(UPLOAD_ID)).thenReturn(List.of());
        when(repository.compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING),
                UploadStatus.COMPLETING)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("minio exploded"))
                .when(storagePort).completeMultipartUpload(any(), any(), anyList());

        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID,
                        List.of(new PartSubmit(1, "\"e1\""), new PartSubmit(2, "\"e2\""))));
        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
        verify(repository).compareAndSetStatus(UPLOAD_ID, Set.of(UploadStatus.COMPLETING), UploadStatus.FAILED);
    }

    @Test
    void throwsWhenUploadExpired() {
        Upload expired = Upload.restored(UPLOAD_ID, USER, null, "clip.mp4", "video/mp4", 26L * 1024 * 1024,
                "videos/" + UPLOAD_ID + "/original.mp4", "storage-upload", UploadStatus.UPLOADING,
                10 * 1024 * 1024, TOTAL_PARTS, Instant.now().minus(Duration.ofSeconds(5)),
                Instant.now().minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(2)), 0L, List.of());
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.of(expired));
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, List.of(new PartSubmit(1, "\"e1\""), new PartSubmit(2, "\"e2\""))));
        assertEquals(ErrorCode.UPLOAD_EXPIRED, ex.getErrorCode());
        verify(storagePort, never()).completeMultipartUpload(any(), any(), anyList());
    }

    @Test
    void throwsNotFoundWhenUploadMissing() {
        when(repository.findById(UPLOAD_ID)).thenReturn(Optional.empty());
        UploadException ex = assertThrows(UploadException.class,
                () -> service.complete(USER, UPLOAD_ID, List.of()));
        assertEquals(ErrorCode.UPLOAD_NOT_FOUND, ex.getErrorCode());
    }
}