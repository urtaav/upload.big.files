package com.videoflow.upload.domain.error;

/**
 * Business error codes exposed by the API. The HTTP status mapping lives in
 * the web layer, so the domain stays free of HTTP concerns.
 */
public enum ErrorCode {

    UPLOAD_NOT_FOUND("Upload session not found."),
    UNAUTHORIZED_UPLOAD("You are not allowed to access this upload."),
    UPLOAD_ALREADY_COMPLETED("The upload is already completed."),
    INVALID_FILE_TYPE("The provided file type is not supported."),
    FILE_TOO_LARGE("The file exceeds the maximum allowed size."),
    INVALID_PART("The requested part or part list is invalid."),
    INVALID_UPLOAD_STATE("The current upload state does not allow this operation."),
    UPLOAD_EXPIRED("The upload session has expired."),
    STORAGE_ERROR("Object storage operation failed."),
    INVALID_REQUEST("The request is invalid."),
    NOT_FOUND("The requested resource was not found."),
    INTERNAL_ERROR("An unexpected error occurred.");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}