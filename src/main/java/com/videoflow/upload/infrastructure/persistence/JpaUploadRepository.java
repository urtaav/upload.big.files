package com.videoflow.upload.infrastructure.persistence;

import com.videoflow.upload.domain.model.Upload;
import com.videoflow.upload.domain.model.UploadPart;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.domain.port.UploadPage;
import com.videoflow.upload.domain.port.UploadRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter. State-changing operations are short, transactional and
 * rely on compare-and-set updates (plus DB unique constraints) so concurrent
 * requests serialize safely.
 */
@Repository
public class JpaUploadRepository implements UploadRepository {

    private static final Set<UploadStatus> OPEN_FOR_PARTS =
            Set.of(UploadStatus.CREATED, UploadStatus.UPLOADING);

    private static final int MAX_PAGE_SIZE = 100;

    private final UploadJpaRepository uploadJpaRepository;
    private final UploadPartJpaRepository partJpaRepository;
    private final UploadEntityMapper mapper;

    public JpaUploadRepository(UploadJpaRepository uploadJpaRepository,
                               UploadPartJpaRepository partJpaRepository,
                               UploadEntityMapper mapper) {
        this.uploadJpaRepository = uploadJpaRepository;
        this.partJpaRepository = partJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Upload save(Upload upload) {
        UploadJpaEntity entity = mapper.toEntity(upload);
        uploadJpaRepository.saveAndFlush(entity);
        return mapper.toDomain(entity, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Upload> findById(UUID id) {
        return uploadJpaRepository.findById(id)
                .map(entity -> mapper.toDomain(entity, findUploadedParts(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Upload> findByIdempotencyKey(UUID userId, String idempotencyKey) {
        return uploadJpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(entity -> mapper.toDomain(entity, findUploadedParts(entity.getId())));
    }

    @Override
    @Transactional
    public boolean compareAndSetStatus(UUID id, Set<UploadStatus> allowedFrom, UploadStatus to) {
        int updated = uploadJpaRepository.transitionStatus(id, allowedFrom, to, Instant.now());
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean saveUploadedPartIfOpen(UUID id, int partNumber, String etag, long size) {
        int touched = uploadJpaRepository.transitionStatus(id, OPEN_FOR_PARTS, UploadStatus.UPLOADING, Instant.now());
        if (touched == 0) {
            return false;
        }
        upsertPart(id, partNumber, etag, size, Instant.now());
        return true;
    }

    @Override
    @Transactional
    public void upsertUploadedParts(UUID id, List<UploadPart> parts) {
        for (UploadPart part : parts) {
            upsertPart(id, part.partNumber(), part.etag(), part.size(), part.uploadedAt());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UploadPart> findUploadedParts(UUID id) {
        return partJpaRepository.findByUploadIdOrderByPartNumber(id).stream()
                .map(mapper::toDomainPart)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUploadedParts(UUID id) {
        return partJpaRepository.countByUploadId(id);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadPage findByUser(UUID userId, Set<UploadStatus> statuses, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<UploadJpaEntity> found = (statuses == null || statuses.isEmpty())
                ? uploadJpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : uploadJpaRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, statuses, pageable);

        // The part list of every row is not needed to render a listing, only
        // how many parts each one has.
        List<Upload> items = found.getContent().stream()
                .map(entity -> mapper.toDomain(entity, List.of()))
                .toList();

        return new UploadPage(items,
                countsFor(found.getContent().stream().map(UploadJpaEntity::getId).toList()),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements());
    }

    private Map<UUID, Long> countsFor(List<UUID> uploadIds) {
        if (uploadIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : partJpaRepository.countGroupedByUploadId(uploadIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private void upsertPart(UUID uploadId, int partNumber, String etag, long size, Instant uploadedAt) {
        UploadPartJpaEntity existing = partJpaRepository.findByUploadIdAndPartNumber(uploadId, partNumber)
                .orElse(null);
        if (existing != null) {
            partJpaRepository.updateEtag(uploadId, partNumber, etag, size, uploadedAt);
        } else {
            partJpaRepository.save(new UploadPartJpaEntity(uploadId, partNumber, etag, size, uploadedAt));
        }
    }
}