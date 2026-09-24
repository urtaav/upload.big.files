package com.videoflow.upload.infrastructure.persistence;

import com.videoflow.upload.domain.model.UploadStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadJpaRepository extends JpaRepository<UploadJpaEntity, UUID> {

    Optional<UploadJpaEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<UploadJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<UploadJpaEntity> findByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId,
                                                                     Collection<UploadStatus> statuses,
                                                                     Pageable pageable);

    /**
     * Compare-and-set state transition used to serialize concurrent complete /
     * cancel / ack flows.
     */
    @Modifying
    @Query("""
            update UploadJpaEntity u
               set u.status = :to, u.updatedAt = :now
             where u.id = :id
               and u.status in :froms
            """)
    int transitionStatus(@Param("id") UUID id,
                         @Param("froms") Collection<UploadStatus> froms,
                         @Param("to") UploadStatus to,
                         @Param("now") Instant now);
}