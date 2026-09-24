package com.videoflow.upload.interfaces.error;

import com.videoflow.upload.domain.error.ErrorCode;
import com.videoflow.upload.domain.error.UploadException;
import com.videoflow.upload.infrastructure.tracing.TraceIdFilter;
import com.videoflow.upload.interfaces.upload.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Translates exceptions into the uniform error body. Business failures carry
 * an {@link ErrorCode}; framework validation and unexpected failures are mapped
 * to generic codes. Never includes stack traces or sensitive data.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<ErrorCode, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry(ErrorCode.UPLOAD_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.UNAUTHORIZED_UPLOAD, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.UPLOAD_ALREADY_COMPLETED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.INVALID_UPLOAD_STATE, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.INVALID_PART, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE),
            Map.entry(ErrorCode.UPLOAD_EXPIRED, HttpStatus.GONE),
            Map.entry(ErrorCode.STORAGE_ERROR, HttpStatus.BAD_GATEWAY),
            Map.entry(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

    @ExceptionHandler(UploadException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadException(UploadException ex, HttpServletRequest request) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(ex.getErrorCode(), HttpStatus.INTERNAL_SERVER_ERROR);
        log.warn("Business error code={} status={} path={} message={}",
                ex.getErrorCode(), status.value(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(status).body(error(status, ex.getErrorCode().name(), ex.getMessage(), request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception ex, HttpServletRequest request) {
        String message = firstMessage(ex);
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST.name(), message, request));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof HttpMessageNotReadableException ? "Malformed request body." : "Invalid request parameter.";
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST.name(), message, request));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingIdentity(ServletRequestBindingException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED_UPLOAD.name(),
                        "Missing user identity.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.name(), "Resource not found.", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(error(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.INVALID_REQUEST.name(),
                        "HTTP method not supported for this path.", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error path={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                        ErrorCode.INTERNAL_ERROR.defaultMessage(), request));
    }

    private ApiErrorResponse error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ApiErrorResponse.of(status.value(), code, message, request.getRequestURI(), traceId());
    }

    private String traceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        return traceId == null ? "" : traceId;
    }

    private String firstMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException mve) {
            return mve.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .orElse("Invalid request body.");
        }
        if (ex instanceof ConstraintViolationException cve) {
            return cve.getConstraintViolations().stream()
                    .findFirst()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .orElse("Invalid request.");
        }
        if (ex instanceof HandlerMethodValidationException hve) {
            return hve.getParameterValidationResults().stream()
                    .flatMap(r -> r.getResolvableErrors().stream())
                    .findFirst()
                    .map(er -> er.getDefaultMessage() == null ? "Invalid request." : er.getDefaultMessage())
                    .orElse("Invalid request.");
        }
        return "Invalid request.";
    }
}