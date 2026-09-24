package com.videoflow.upload.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping of the upload_parts table. Parts belong to an upload through the
 * {@code uploadId} column (the FK is declared in migrations); the unique
 * constraint on (upload_id, part_number) makes acknowledgements idempotent.
 */
@Entity
@Table(name = "upload_parts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_upload_parts_number", columnNames = {"upload_id", "part_number"})
})
public class UploadPartJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "upload_id", nullable = false, columnDefinition = "uuid")
    private UUID uploadId;

    @Column(name = "part_number", nullable = false)
    private int partNumber;

    @Column(name = "etag", nullable = false, length = 128)
    private String etag;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected UploadPartJpaEntity() {
    }

    public UploadPartJpaEntity(UUID uploadId, int partNumber, String etag, long size, Instant uploadedAt) {
        this.uploadId = uploadId;
        this.partNumber = partNumber;
        this.etag = etag;
        this.size = size;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public void setUploadId(UUID uploadId) {
        this.uploadId = uploadId;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}