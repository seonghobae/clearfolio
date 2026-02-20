package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.ApiErrorResponse;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleUnsupportedIncludesExtensionAndHeaderTraceId() {
        HttpServletRequest request = request("trace-header", "request-1");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnsupported(
                new UnsupportedDocumentFormatException("hwp"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("UNSUPPORTED_FORMAT", body.errorCode());
        assertTrue(body.message().contains("hwp"));
        assertEquals("trace-header", body.traceId());
        assertEquals("hwp", body.details().get("extension"));
    }

    @Test
    void handleBadRequestFallsBackToRequestIdWhenHeaderBlank() {
        HttpServletRequest request = request("  ", "request-2");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("bad input"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("bad input", body.message());
        assertEquals("request-2", body.traceId());
        assertTrue(body.details().isEmpty());
    }

    @Test
    void handleMaxUploadIncludesConfiguredMaxUploadSize() {
        HttpServletRequest request = request("trace-3", "request-3");

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(1024L),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("File is too large.", body.message());
        assertEquals(1024L, body.details().get("maxUploadSize"));
    }

    @Test
    void handleMissingRequestPartIncludesPartName() throws Exception {
        HttpServletRequest request = request("trace-4", "request-4");

        ResponseEntity<ApiErrorResponse> response = handler.handleMissingServletRequestPart(
                new MissingServletRequestPartException("file"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("file", body.details().get("part"));
    }

    @Test
    void handleTypeMismatchSerializesNullParameterValue() {
        HttpServletRequest request = request("trace-5", "request-5");
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                null,
                UUID.class,
                "jobId",
                null,
                new IllegalArgumentException("bad uuid")
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(mismatch, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("Invalid value for parameter jobId", body.message());
        assertEquals("jobId", body.details().get("parameter"));
        assertEquals("null", body.details().get("value"));
    }

    @Test
    void handleResponseStatusNormalizesEnumCodeAndUsesReason() {
        HttpServletRequest request = request("trace-6", "request-6");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"),
                request
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("NOT_FOUND", body.errorCode());
        assertEquals("job not found", body.message());
    }

    @Test
    void handleResponseStatusKeepsCustomCodeWithoutSeparatorAndGeneratesTraceId() {
        HttpServletRequest request = request(null, " ");
        HttpStatusCode customStatus = HttpStatusCode.valueOf(499);

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(customStatus, "custom reason"),
                request
        );

        assertEquals(499, response.getStatusCode().value());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("499", body.errorCode());
        assertEquals("custom reason", body.message());
        assertNotNull(body.traceId());
        assertFalse(body.traceId().isBlank());
    }

    @Test
    void handleUnexpectedReturnsInternalErrorPayload() {
        HttpServletRequest request = request("trace-7", "request-7");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("boom"),
                request
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.errorCode());
        assertEquals("Unexpected error", body.message());
        assertEquals("trace-7", body.traceId());
        assertTrue(body.details().isEmpty());
    }

    @Test
    void handleBadRequestGeneratesTraceIdWhenHeaderAndRequestIdAreMissing() {
        HttpServletRequest request = request(null, null);

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("bad input"),
                request
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("bad input", body.message());
        assertNotNull(body.traceId());
        assertFalse(body.traceId().isBlank());
    }

    @Test
    void handleUnexpectedSupportsNullRequestUri() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Trace-Id")).thenReturn(null);
        when(request.getRequestId()).thenReturn(null);
        when(request.getRequestURI()).thenReturn(null);

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("boom"),
                request
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.errorCode());
        assertNotNull(body.traceId());
        assertFalse(body.traceId().isBlank());
    }

    private HttpServletRequest request(String headerTraceId, String requestId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Trace-Id")).thenReturn(headerTraceId);
        when(request.getRequestId()).thenReturn(requestId);
        when(request.getRequestURI()).thenReturn("/test/path");
        return request;
    }
}
