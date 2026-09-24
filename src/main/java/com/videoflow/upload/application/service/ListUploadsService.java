package com.videoflow.upload.application.service;

import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.UploadPage;
import com.videoflow.upload.domain.port.UploadRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lists the upload sessions a user owns, newest first.
 *
 * <p>Scoping is not optional: the repository query is always bound to the
 * caller's id, so there is no code path that returns another user's rows.
 */
@Service
public class ListUploadsService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UploadRepository uploadRepository;

    public ListUploadsService(UploadRepository uploadRepository) {
        this.uploadRepository = uploadRepository;
    }

    public UploadPage list(UUID userId, Set<UploadStatus> statuses, Integer page, Integer size) {
        return uploadRepository.findByUser(
                userId,
                statuses,
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size);
    }
}
