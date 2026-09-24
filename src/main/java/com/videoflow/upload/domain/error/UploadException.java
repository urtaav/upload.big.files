package com.videoflow.upload.domain.error;

/**
 * Domain-level exception carrying a business {@link ErrorCode}.
 */
public class UploadException extends RuntimeException {

    private final ErrorCode errorCode;

    public UploadException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public UploadException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public UploadException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}