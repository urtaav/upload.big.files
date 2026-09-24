package com.videoflow.upload.domain.port;

import java.time.Duration;
import java.util.List;

/**
 * Port for the object storage service (S3-compatible; MinIO in development).
 *
 * <p>The domain and application layers depend only on this interface, never on
 * the AWS SDK or MinIO classes.
 */
public interface StoragePort {

    /**
     * Initiates a multipart upload and returns the storage-side upload id.
     */
    String createMultipartUpload(String objectKey, String contentType);

    /**
     * Builds a time-limited presigned URL that lets a client PUT a single part
     * directly to the storage endpoint.
     */
    String presignUploadPart(String objectKey, String uploadId, int partNumber, Duration ttl);

    /**
     * Assembles all parts into the final object.
     */
    void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPart> parts);

    /**
     * Discards a multipart upload and its already uploaded parts.
     */
    void abortMultipartUpload(String objectKey, String uploadId);

    /**
     * Builds a time-limited presigned URL that lets a client GET the finished
     * object directly from storage. The download name and content type are
     * pinned into the signature so the browser saves the file under the name
     * the user recognises, whatever the storage key looks like.
     */
    String presignDownload(String objectKey, String downloadFileName, String contentType, Duration ttl);

    record CompletedPart(int partNumber, String etag) {
    }
}