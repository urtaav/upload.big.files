package com.videoflow.upload.interfaces.upload;

import com.videoflow.upload.application.result.CreateUploadResult;
import com.videoflow.upload.application.result.PartSubmit;
import com.videoflow.upload.application.result.PartAckResult;
import com.videoflow.upload.application.result.PresignedPart;
import com.videoflow.upload.application.service.CancelUploadService;
import com.videoflow.upload.application.service.CompleteUploadService;
import com.videoflow.upload.application.service.CreateUploadService;
import com.videoflow.upload.application.service.GenerateDownloadUrlService;
import com.videoflow.upload.application.service.GeneratePresignedUrlsService;
import com.videoflow.upload.application.service.GetUploadService;
import com.videoflow.upload.application.service.ListUploadsService;
import com.videoflow.upload.application.service.RecordUploadedPartService;
import com.videoflow.upload.domain.model.UploadStatus;
import com.videoflow.upload.infrastructure.security.RequestAttributes;
import com.videoflow.upload.interfaces.upload.dto.request.AckPartRequest;
import com.videoflow.upload.interfaces.upload.dto.request.CompleteUploadRequest;
import com.videoflow.upload.interfaces.upload.dto.request.CreateUploadRequest;
import com.videoflow.upload.interfaces.upload.dto.request.PresignPartsRequest;
import com.videoflow.upload.interfaces.upload.dto.response.DownloadUrlResponse;
import com.videoflow.upload.interfaces.upload.dto.response.PartAckResponse;
import com.videoflow.upload.interfaces.upload.dto.response.PresignedPartsResponse;
import com.videoflow.upload.interfaces.upload.dto.response.UploadListResponse;
import com.videoflow.upload.interfaces.upload.dto.response.UploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the upload orchestration use cases. No file bytes ever enter this
 * controller.
 */
@Tag(name = "Uploads", description = "Direct-to-storage large file upload orchestration")
@RestController
@RequestMapping("/v1/uploads")
@Validated
public class UploadController {

    private final CreateUploadService createUploadService;
    private final GeneratePresignedUrlsService generatePresignedUrlsService;
    private final GetUploadService getUploadService;
    private final ListUploadsService listUploadsService;
    private final GenerateDownloadUrlService generateDownloadUrlService;
    private final RecordUploadedPartService recordUploadedPartService;
    private final CompleteUploadService completeUploadService;
    private final CancelUploadService cancelUploadService;
    private final UploadResponseMapper responseMapper;

    public UploadController(CreateUploadService createUploadService,
                            GeneratePresignedUrlsService generatePresignedUrlsService,
                            GetUploadService getUploadService,
                            ListUploadsService listUploadsService,
                            GenerateDownloadUrlService generateDownloadUrlService,
                            RecordUploadedPartService recordUploadedPartService,
                            CompleteUploadService completeUploadService,
                            CancelUploadService cancelUploadService,
                            UploadResponseMapper responseMapper) {
        this.createUploadService = createUploadService;
        this.generatePresignedUrlsService = generatePresignedUrlsService;
        this.getUploadService = getUploadService;
        this.listUploadsService = listUploadsService;
        this.generateDownloadUrlService = generateDownloadUrlService;
        this.recordUploadedPartService = recordUploadedPartService;
        this.completeUploadService = completeUploadService;
        this.cancelUploadService = cancelUploadService;
        this.responseMapper = responseMapper;
    }

    @Operation(summary = "Create an upload session")
    @PostMapping
    public ResponseEntity<UploadResponse> create(
            @Valid @RequestBody CreateUploadRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64) String idempotencyKey,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {

        CreateUploadResult result = createUploadService.create(
                userId, idempotencyKey, request.fileName(), request.contentType(), request.size());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(responseMapper.toResponse(result.snapshot()));
    }

    @Operation(summary = "Get presigned upload URLs for specific parts")
    @PostMapping("/{uploadId}/parts")
    public PresignedPartsResponse getPartUploadUrls(
            @PathVariable UUID uploadId,
            @Valid @RequestBody PresignPartsRequest request,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {

        List<PresignedPart> parts = generatePresignedUrlsService.generate(userId, uploadId, request.partNumbers());
        return new PresignedPartsResponse(uploadId, parts);
    }

    @Operation(summary = "List the caller's uploads, newest first",
            description = "Always scoped to the caller. Use 'status' to narrow the listing, for example "
                    + "status=COMPLETED for finished files or status=CREATED,UPLOADING for resumable ones.")
    @GetMapping
    public UploadListResponse list(
            @RequestParam(name = "status", required = false) Set<UploadStatus> statuses,
            @RequestParam(name = "page", required = false) @Min(0) Integer page,
            @RequestParam(name = "size", required = false) @Min(1) @Max(100) Integer size,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {

        return responseMapper.toResponse(listUploadsService.list(userId, statuses, page, size));
    }

    @Operation(summary = "Get a presigned URL to download the finished object",
            description = "The object is served straight from storage under its original file name. "
                    + "The API never streams the bytes.")
    @GetMapping("/{uploadId}/download")
    public DownloadUrlResponse download(
            @PathVariable UUID uploadId,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {

        return responseMapper.toResponse(generateDownloadUrlService.generate(userId, uploadId));
    }

    @Operation(summary = "Get upload status and uploaded parts")
    @GetMapping("/{uploadId}")
    public UploadResponse get(
            @PathVariable UUID uploadId,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {
        return responseMapper.toResponse(getUploadService.get(userId, uploadId));
    }

    @Operation(summary = "Acknowledge an uploaded part")
    @PostMapping("/{uploadId}/parts/{partNumber}/ack")
    public PartAckResponse ackPart(
            @PathVariable UUID uploadId,
            @PathVariable int partNumber,
            @Valid @RequestBody AckPartRequest request,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {

        PartAckResult result = recordUploadedPartService.ack(
                userId, uploadId, partNumber, request.etag(), request.size());
        return new PartAckResponse(result.uploadId(), result.partNumber(), result.etag(), result.uploadedParts());
    }

    @Operation(summary = "Complete the multipart upload")
    @PostMapping("/{uploadId}/complete")
    public UploadResponse complete(
            @PathVariable UUID uploadId,
            @Valid @RequestBody CompleteUploadRequest request,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {

        List<PartSubmit> parts = request.parts().stream()
                .map(p -> new PartSubmit(p.partNumber(), p.etag()))
                .toList();
        return responseMapper.toResponse(completeUploadService.complete(userId, uploadId, parts));
    }

    @Operation(summary = "Cancel an upload session")
    @DeleteMapping("/{uploadId}")
    public UploadResponse cancel(
            @PathVariable UUID uploadId,
            @RequestAttribute(RequestAttributes.CURRENT_USER_ID) UUID userId) {
        return responseMapper.toResponse(cancelUploadService.cancel(userId, uploadId));
    }
}