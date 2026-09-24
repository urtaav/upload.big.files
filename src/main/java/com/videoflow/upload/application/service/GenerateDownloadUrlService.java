package com.videoflow.upload.application.service;

import com.videoflow.upload.application.config.UploadSettings;
import com.videoflow.upload.application.result.DownloadLink;
import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.StoragePort;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues a presigned GET URL for a finished object.
 *
 * <p>The API hands out a signed link instead of streaming the file, for the
 * same reason it never received the upload bytes: keeping large transfers out
 * of the application process. The original file name is pinned into the
 * signature, so the browser saves it under the name the user knows even though
 * the storage key is sanitised.
 */
@Service
public class GenerateDownloadUrlService {

    /** Only a fully assembled object can be downloaded. */
    private static final Set<UploadStatus> DOWNLOADABLE =
            Set.of(UploadStatus.COMPLETED, UploadStatus.PROCESSING, UploadStatus.READY);

    private final StoragePort storagePort;
    private final UploadAccessGuard accessGuard;
    private final UploadSettings settings;

    public GenerateDownloadUrlService(StoragePort storagePort,
                                      UploadAccessGuard accessGuard,
                                      UploadSettings settings) {
        this.storagePort = storagePort;
        this.accessGuard = accessGuard;
        this.settings = settings;
    }

    public DownloadLink generate(UUID userId, UUID uploadId) {
        Upload upload = accessGuard.requireOwned(uploadId, userId);

        if (!DOWNLOADABLE.contains(upload.getStatus())) {
            throw new UploadException(ErrorCode.INVALID_UPLOAD_STATE,
                    "An upload in state " + upload.getStatus() + " has no object to download yet.");
        }

        String url;
        try {
            url = storagePort.presignDownload(
                    upload.getObjectKey(),
                    upload.getFileName(),
                    upload.getContentType(),
                    settings.presignedUrlTtl());
        } catch (RuntimeException ex) {
            throw new UploadException(ErrorCode.STORAGE_ERROR, "Could not sign a download URL.", ex);
        }

        return new DownloadLink(
                upload.getId(),
                upload.getFileName(),
                upload.getContentType(),
                upload.getSize(),
                url,
                Instant.now().plus(settings.presignedUrlTtl()));
    }
}
