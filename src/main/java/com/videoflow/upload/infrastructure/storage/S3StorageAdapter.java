package com.videoflow.upload.infrastructure.storage;

import com.videoflow.upload.domain.port.StoragePort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * Storage adapter over the AWS S3 SDK v2, pointed at any S3-compatible
 * endpoint (MinIO in development, Amazon S3 in production). This is the only
 * place that knows about the S3 SDK.
 */
@Component
public class S3StorageAdapter implements StoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;

    public S3StorageAdapter(S3Client s3Client, S3Presigner s3Presigner, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public String createMultipartUpload(String objectKey, String contentType) {
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build());
        return response.uploadId();
    }

    @Override
    public String presignUploadPart(String objectKey, String uploadId, int partNumber, Duration ttl) {
        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(
                UploadPartPresignRequest.builder()
                        .signatureDuration(ttl)
                        .uploadPartRequest(UploadPartRequest.builder()
                                .bucket(properties.bucket())
                                .key(objectKey)
                                .uploadId(uploadId)
                                .partNumber(partNumber)
                                .build())
                        .build());
        return presigned.url().toString();
    }

    @Override
    public void completeMultipartUpload(String objectKey, String uploadId, List<CompletedPart> parts) {
        List<software.amazon.awssdk.services.s3.model.CompletedPart> completedParts = parts.stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .map(p -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                        .partNumber(p.partNumber())
                        .eTag(p.etag())
                        .build())
                .toList();
        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build())
                .build());
    }

    @Override
    public String presignDownload(String objectKey, String downloadFileName, String contentType, Duration ttl) {
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(objectKey)
                                .responseContentDisposition(contentDisposition(downloadFileName))
                                .responseContentType(contentType)
                                .build())
                        .build());
        return presigned.url().toString();
    }

    /**
     * RFC 6266: a plain ASCII fallback plus a UTF-8 variant, so a name with
     * accents survives browsers that only read one of the two.
     */
    private String contentDisposition(String fileName) {
        String name = (fileName == null || fileName.isBlank()) ? "download" : fileName;
        String ascii = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .uploadId(uploadId)
                .build());
    }
}