package com.videoflow.upload.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.application.result.DownloadLink;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.StoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerateDownloadUrlServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID UPLOAD = UUID.randomUUID();
    private static final Duration TTL = Duration.ofMinutes(15);

    private StoragePort storagePort;
    private UploadAccessGuard accessGuard;
    private GenerateDownloadUrlService service;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);
        accessGuard = mock(UploadAccessGuard.class);
        UploadSettings settings = new UploadSettings(5L * 1024 * 1024 * 1024, 10 * 1024 * 1024,
                List.of("*"), TTL, Duration.ofHours(24));
        service = new GenerateDownloadUrlService(storagePort, accessGuard, settings);
    }

    private Upload uploadWith(UploadStatus status) {
        Instant now = Instant.now();
        return Upload.restored(UPLOAD, USER, null, "Vacaciones 2026.mp4", "video/mp4", 2048L,
                "uploads/" + UPLOAD + "/vacaciones-2026.mp4", "storage-upload", status,
                10 * 1024 * 1024, 1, now.plus(Duration.ofHours(1)), now, now, 0L, List.of());
    }

    @Test
    void signsTheDownloadUnderTheOriginalFileName() {
        when(accessGuard.requireOwned(UPLOAD, USER)).thenReturn(uploadWith(UploadStatus.COMPLETED));
        when(storagePort.presignDownload(any(), any(), any(), any())).thenReturn("https://storage.test/signed");

        DownloadLink link = service.generate(USER, UPLOAD);

        assertEquals("https://storage.test/signed", link.url());
        assertEquals("Vacaciones 2026.mp4", link.fileName());
        // The sanitised key goes to storage; the name the user knows goes into
        // the signature, which is what the browser will save the file as.
        verify(storagePort).presignDownload(
                eq("uploads/" + UPLOAD + "/vacaciones-2026.mp4"),
                eq("Vacaciones 2026.mp4"),
                eq("video/mp4"),
                eq(TTL));
    }

    @Test
    void refusesToSignAnUploadThatIsNotAssembledYet() {
        when(accessGuard.requireOwned(UPLOAD, USER)).thenReturn(uploadWith(UploadStatus.UPLOADING));

        UploadException ex = assertThrows(UploadException.class, () -> service.generate(USER, UPLOAD));

        assertEquals(ErrorCode.INVALID_UPLOAD_STATE, ex.getErrorCode());
        verify(storagePort, never()).presignDownload(any(), any(), any(), any());
    }

    @Test
    void refusesCancelledUploads() {
        when(accessGuard.requireOwned(UPLOAD, USER)).thenReturn(uploadWith(UploadStatus.CANCELLED));

        assertThrows(UploadException.class, () -> service.generate(USER, UPLOAD));
    }

    @Test
    void wrapsStorageFailureAsStorageError() {
        when(accessGuard.requireOwned(UPLOAD, USER)).thenReturn(uploadWith(UploadStatus.COMPLETED));
        when(storagePort.presignDownload(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("presigner exploded"));

        UploadException ex = assertThrows(UploadException.class, () -> service.generate(USER, UPLOAD));

        assertEquals(ErrorCode.STORAGE_ERROR, ex.getErrorCode());
    }
}
