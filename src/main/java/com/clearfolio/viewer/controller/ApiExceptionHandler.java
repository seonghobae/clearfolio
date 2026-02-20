package com.clearfolio.viewer.controller;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;
import com.clearfolio.viewer.api.ApiErrorResponse;

/**
 * Maps application exceptions to stable API error responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Handles blocked or unsupported document format requests.
     *
     * @param ex thrown format exception
     * @param request current HTTP request
     * @return bad request response with format details
     */
    @ExceptionHandler(UnsupportedDocumentFormatException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupported(
            UnsupportedDocumentFormatException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "UNSUPPORTED_FORMAT",
                        ex.getMessage(),
                        resolveTraceId(request),
                        Map.of("extension", ex.getExtension())
                ));
    }

    /**
     * Handles generic validation failures represented as illegal arguments.
     *
     * @param ex thrown bad input exception
     * @param request current HTTP request
     * @return bad request response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        ex.getMessage(),
                        resolveTraceId(request),
                        Map.of()
                ));
    }

    /**
     * Handles uploads that exceed configured multipart limits.
     *
     * @param ex thrown size limit exception
     * @param request current HTTP request
     * @return bad request response with size constraint details
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        "File is too large.",
                        resolveTraceId(request),
                        Map.of("maxUploadSize", ex.getMaxUploadSize())
                ));
    }

    /**
     * Handles requests missing required multipart parts.
     *
     * @param ex thrown missing part exception
     * @param request current HTTP request
     * @return bad request response with missing part details
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        ex.getMessage(),
                        resolveTraceId(request),
                        Map.of("part", ex.getRequestPartName())
                ));
    }

    /**
     * Handles path or query parameter type mismatches.
     *
     * @param ex thrown type mismatch exception
     * @param request current HTTP request
     * @return bad request response with parameter diagnostics
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        "Invalid value for parameter " + ex.getName(),
                        resolveTraceId(request),
                        Map.of(
                                "parameter", ex.getName(),
                                "value", String.valueOf(ex.getValue())
                        )
                ));
    }

    /**
     * Handles propagated Spring response status exceptions.
     *
     * @param ex thrown response status exception
     * @param request current HTTP request
     * @return response preserving the original status code and reason
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiErrorResponse(
                        normalizeStatusCode(ex.getStatusCode()),
                        ex.getReason(),
                        resolveTraceId(request),
                        Map.of()
                ));
    }

    /**
     * Handles uncaught exceptions with a generic internal error payload.
     *
     * @param ex unexpected exception
     * @param request current HTTP request
     * @return internal server error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        LOGGER.error("Unexpected error on path={} traceId={}", request.getRequestURI(), traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_ERROR",
                        "Unexpected error",
                        traceId,
                        Map.of()
                ));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String header = request.getHeader("X-Trace-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }

        String requestId = request.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }

        return UUID.randomUUID().toString();
    }

    private String normalizeStatusCode(HttpStatusCode statusCode) {
        String value = statusCode.toString();
        int separatorIndex = value.indexOf(' ');
        if (separatorIndex < 0) {
            return value;
        }
        return value.substring(separatorIndex + 1);

    }
}
