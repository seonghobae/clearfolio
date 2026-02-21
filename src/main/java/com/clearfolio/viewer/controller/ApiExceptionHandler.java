package com.clearfolio.viewer.controller;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
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

    @Value("${conversion.max-upload-size-bytes:5242880}")
    private long configuredMaxUploadSize = 5242880L;

    /**
     * Handles blocked or unsupported document format requests.
     *
     * @param ex thrown format exception
     * @param exchange current HTTP exchange
     * @return bad request response with format details
     */
    @ExceptionHandler(UnsupportedDocumentFormatException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupported(
            UnsupportedDocumentFormatException ex,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "UNSUPPORTED_FORMAT",
                        ex.getMessage(),
                        resolveTraceId(exchange),
                        extensionDetails(ex.getExtension())
                ));
    }

    /**
     * Handles generic validation failures represented as illegal arguments.
     *
     * @param ex thrown bad input exception
     * @param exchange current HTTP exchange
     * @return bad request response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            IllegalArgumentException ex,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        ex.getMessage(),
                        resolveTraceId(exchange),
                        Map.of()
                ));
    }

    /**
     * Handles uploads that exceed configured multipart limits.
     *
     * @param ex thrown size limit exception
     * @param exchange current HTTP exchange
     * @return bad request response with size constraint details
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        "File is too large.",
                        resolveTraceId(exchange),
                        Map.of("maxUploadSize", resolveMaxUploadSize(ex.getMaxUploadSize()))
                ));
    }

    /**
     * Handles requests that fail web input binding and validation.
     *
     * @param ex thrown web input exception
     * @param exchange current HTTP exchange
     * @return bad request response with optional part details
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleServerWebInput(
            ServerWebInputException ex,
            ServerWebExchange exchange) {
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = "Bad request";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        message,
                        resolveTraceId(exchange),
                        missingPartDetails(message)
                ));
    }

    /**
     * Handles payloads that exceed reactive in-memory buffering limits.
     *
     * @param ex thrown data buffer limit exception
     * @param exchange current HTTP exchange
     * @return bad request response
     */
    @ExceptionHandler(DataBufferLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleDataBufferLimit(
            DataBufferLimitException ex,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        "File is too large.",
                        resolveTraceId(exchange),
                        Map.of("maxUploadSize", resolveMaxUploadSize(-1L))
                ));
    }

    /**
     * Handles path or query parameter type mismatches.
     *
     * @param ex thrown type mismatch exception
     * @param exchange current HTTP exchange
     * @return bad request response with parameter diagnostics
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "BAD_REQUEST",
                        "Invalid value for parameter " + ex.getName(),
                        resolveTraceId(exchange),
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
     * @param exchange current HTTP exchange
     * @return response preserving the original status code and reason
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            ServerWebExchange exchange) {
        String reason = ex.getReason();
        if (reason == null || reason.isBlank()) {
            reason = "HTTP " + ex.getStatusCode().value();
        }
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiErrorResponse(
                        normalizeStatusCode(ex.getStatusCode()),
                        reason,
                        resolveTraceId(exchange),
                        Map.of()
                ));
    }

    /**
     * Handles uncaught exceptions with a generic internal error payload.
     *
     * @param ex unexpected exception
     * @param exchange current HTTP exchange
     * @return internal server error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        URI requestUri = exchange.getRequest().getURI();
        String path = requestUri == null ? "" : requestUri.getRawPath();
        LOGGER.error(
                "Unexpected error on path={} traceId={}",
                sanitizeForLog(path),
                sanitizeForLog(traceId),
                ex
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_ERROR",
                        "Unexpected error",
                        traceId,
                        Map.of()
                ));
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }

        String requestId = exchange.getRequest().getId();
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }

        return UUID.randomUUID().toString();
    }

    private String normalizeStatusCode(HttpStatusCode statusCode) {
        int code = statusCode.value();
        HttpStatus resolved = HttpStatus.resolve(code);
        if (resolved == null) {
            return Integer.toString(code);
        }
        return resolved.name();
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u0000', '_')
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\u2028', '_')
                .replace('\u2029', '_')
                .replace('\u202A', '_')
                .replace('\u202B', '_')
                .replace('\u202C', '_')
                .replace('\u202D', '_')
                .replace('\u202E', '_');
    }

    private Map<String, Object> extensionDetails(String extension) {
        if (extension == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("extension", extension);
        return details;
    }

    private Map<String, Object> missingPartDetails(String message) {
        if (message == null) {
            return Map.of();
        }

        String prefix = "Required request part '";
        int partStart = message.indexOf(prefix);
        if (partStart < 0) {
            return Map.of();
        }

        int nameStart = partStart + prefix.length();
        int nameEnd = message.indexOf('\'', nameStart);
        if (nameEnd < 0) {
            return Map.of();
        }

        String partName = message.substring(nameStart, nameEnd);
        if (partName.isBlank()) {
            return Map.of();
        }

        return Map.of("part", partName);
    }

    private long resolveMaxUploadSize(long exceptionMaxUploadSize) {
        return exceptionMaxUploadSize > 0 ? exceptionMaxUploadSize : configuredMaxUploadSize;
    }
}
