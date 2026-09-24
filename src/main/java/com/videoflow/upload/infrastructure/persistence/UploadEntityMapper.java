package com.videoflow.upload.infrastructure.persistence;

import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps between JPA entities and the plain domain aggregates.
 */
@Component
public class UploadEntityMapper {

    public UploadJpaEntity toEntity(Upload upload) {
        UploadJpaEntity entity = new UploadJpaEntity();
        entity.setId(upload.getId());
        entity.setUserId(upload.getUserId());
        entity.setIdempotencyKey(upload.getIdempotencyKey());
        entity.setFileName(upload.getFileName());
        entity.setContentType(upload.getContentType());
        entity.setSize(upload.getSize());
        entity.setObjectKey(upload.getObjectKey());
        entity.setStorageUploadId(upload.getStorageUploadId());
        entity.setStatus(upload.getStatus());
        entity.setPartSize(upload.getPartSize());
        entity.setTotalParts(upload.getTotalParts());
        entity.setExpiresAt(upload.getExpiresAt());
        entity.setCreatedAt(upload.getCreatedAt());
        entity.setUpdatedAt(upload.getUpdatedAt());
        entity.setVersion(upload.getVersion());
        return entity;
    }

    public Upload toDomain(UploadJpaEntity entity, List<UploadPart> parts) {
        return Upload.restored(entity.getId(), entity.getUserId(), entity.getIdempotencyKey(),
                entity.getFileName(), entity.getContentType(), entity.getSize(), entity.getObjectKey(),
                entity.getStorageUploadId(), entity.getStatus(), entity.getPartSize(), entity.getTotalParts(),
                entity.getExpiresAt(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion(), parts);
    }

    public UploadPart toDomainPart(UploadPartJpaEntity entity) {
        return new UploadPart(entity.getPartNumber(), entity.getEtag(), entity.getSize(), entity.getUploadedAt());
    }
}