package com.videoflow.upload.domain.port;

import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
import com.videoflow.upload.domain.model.UploadStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port for persisting upload sessions and their parts.
 */
public interface UploadRepository {

    /**
     * Persists a new upload session.
     */
    Upload save(Upload upload);

    Optional<Upload> findById(UUID id);

    Optional<Upload> findByIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Compare-and-set state transition: updates {@code status} from any of the
     * {@code allowedFrom} states to {@code to}. Returns {@code true} only if a
     * row was actually updated, which serializes concurrent state changes.
     */
    boolean compareAndSetStatus(UUID id, Set<UploadStatus> allowedFrom, UploadStatus to);

    /**
     * Registers an uploaded part only if the upload is still accepting parts
     * (CREATED or UPLOADING); also moves CREATED to UPLOADING. Returns
     * {@code false} when the current state does not allow registering parts.
     */
    boolean saveUploadedPartIfOpen(UUID id, int partNumber, String etag, long size);

    /**
     * Upserts the ETags reported at completion time (used when the client never
     * sent per-part acknowledgements).
     */
    void upsertUploadedParts(UUID id, List<UploadPart> parts);

    List<UploadPart> findUploadedParts(UUID id);

    /**
     * Lists the sessions owned by a user, newest first, optionally narrowed to
     * a set of statuses. An empty or null status set means "every status".
     */
    UploadPage findByUser(UUID userId, Set<UploadStatus> statuses, int page, int size);

    long countUploadedParts(UUID id);
}