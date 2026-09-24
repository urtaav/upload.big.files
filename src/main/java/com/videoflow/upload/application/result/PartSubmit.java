package com.videoflow.upload.application.result;

/**
 * A part as reported by the client at completion time.
 */
public record PartSubmit(int partNumber, String etag) {
}