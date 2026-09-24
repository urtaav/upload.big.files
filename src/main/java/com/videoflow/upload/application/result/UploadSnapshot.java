package com.videoflow.upload.application.result;

import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
import java.util.List;

/**
 * An upload aggregate plus its known uploaded parts, ready to be mapped to a
 * response DTO.
 */
public record UploadSnapshot(Upload upload, List<UploadPart> uploadedParts) {

    public long uploadedPartsCount() {
        return uploadedParts == null ? 0L : uploadedParts.size();
    }
}