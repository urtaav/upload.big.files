package com.videoflow.upload.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadPartJpaRepository extends JpaRepository<UploadPartJpaEntity, Long> {

    Optional<UploadPartJpaEntity> findByUploadIdAndPartNumber(UUID uploadId, int partNumber);

    List<UploadPartJpaEntity> findByUploadIdOrderByPartNumber(UUID uploadId);

    long countByUploadId(UUID uploadId);

    /**
     * Acknowledged part counts for a whole page of uploads in one round trip,
     * so a listing never degrades into one count query per row.
     */
    @Query("""
            select p.uploadId, count(p)
              from UploadPartJpaEntity p
             where p.uploadId in :uploadIds
             group by p.uploadId
            """)
    List<Object[]> countGroupedByUploadId(@Param("uploadIds") Collection<UUID> uploadIds);

    @Modifying
    @Query("""
            update UploadPartJpaEntity p
               set p.etag = :etag, p.size = :size, p.uploadedAt = :uploadedAt
             where p.uploadId = :uploadId
               and p.partNumber = :partNumber
            """)
    int updateEtag(@Param("uploadId") UUID uploadId,
                   @Param("partNumber") int partNumber,
                   @Param("etag") String etag,
                   @Param("size") long size,
                   @Param("uploadedAt") Instant uploadedAt);
}