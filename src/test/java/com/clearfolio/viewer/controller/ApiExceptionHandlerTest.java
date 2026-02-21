package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import com.clearfolio.viewer.api.ApiErrorResponse;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleUnsupportedIncludesExtensionAndHeaderTraceId() {
        ServerWebExchange exchange = exchange("trace-header", "request-1");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnsupported(
                new UnsupportedDocumentFormatException("hwp"),
                exchange
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
    void handleUnsupportedOmitsExtensionDetailWhenExtensionIsNull() {
        ServerWebExchange exchange = exchange("trace-header-null-ext", "request-null-ext");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnsupported(
                new UnsupportedDocumentFormatException(null),
                exchange
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("UNSUPPORTED_FORMAT", body.errorCode());
        assertEquals("This document type is blocked by policy.", body.message());
        assertTrue(body.details().isEmpty());
    }

    @Test
    void sanitizeForLogReplacesControlCharacters() throws Exception {
        Method method = ApiExceptionHandler.class.getDeclaredMethod("sanitizeForLog", String.class);
        method.setAccessible(true);

        String sanitized = (String) method.invoke(handler, "a\u0000b\rc\nd\u2028e\u2029f\u202Eg");

        assertEquals("a_b_c_d_e_f_g", sanitized);
    }

    @Test
    void sanitizeForLogReturnsEmptyStringForNullValue() throws Exception {
        Method method = ApiExceptionHandler.class.getDeclaredMethod("sanitizeForLog", String.class);
        method.setAccessible(true);

        String sanitized = (String) method.invoke(handler, new Object[] {null});

        assertEquals("", sanitized);
    }

    @Test
    void handleBadRequestFallsBackToRequestIdWhenHeaderBlank() {
        ServerWebExchange exchange = exchange("  ", "request-2");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("bad input"),
                exchange
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
        ServerWebExchange exchange = exchange("trace-3", "request-3");

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(1024L),
                exchange
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("File is too large.", body.message());
        assertEquals(1024L, body.details().get("maxUploadSize"));
    }

    @Test
    void handleServerWebInputIncludesMissingPartNameWhenAvailable() {
        ServerWebExchange exchange = exchange("trace-4", "request-4");
        ServerWebInputException error = new ServerWebInputException("Required request part 'file' is not present");

        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInput(error, exchange);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("file", body.details().get("part"));
    }

    @Test
    void handleServerWebInputFallsBackToBadRequestWhenReasonMissing() {
        ServerWebExchange exchange = exchange("trace-4b", "request-4b");
        ServerWebInputException error = new ServerWebInputException((String) null);

        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInput(error, exchange);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("Bad request", body.message());
        assertTrue(body.details().isEmpty());
    }

    @Test
    void handleServerWebInputFallsBackToBadRequestWhenReasonIsBlank() {
        ServerWebExchange exchange = exchange("trace-4bb", "request-4bb");
        ServerWebInputException error = new ServerWebInputException("   ");

        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInput(error, exchange);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("Bad request", body.message());
        assertTrue(body.details().isEmpty());
    }

    @Test
    void handleServerWebInputOmitsPartDetailsWhenPatternDoesNotMatch() {
        ServerWebExchange exchange = exchange("trace-4c", "request-4c");
        ServerWebInputException error = new ServerWebInputException("payload malformed");

        ResponseEntity<ApiErrorResponse> response = handler.handleServerWebInput(error, exchange);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("payload malformed", body.message());
        assertTrue(body.details().isEmpty());
    }

    @Test
    void missingPartDetailsHandlesMalformedPatterns() throws Exception {
        Method method = ApiExceptionHandler.class.getDeclaredMethod("missingPartDetails", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> nullDetails =
                (java.util.Map<String, Object>) method.invoke(handler, new Object[] {null});
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> unclosedQuoteDetails =
                (java.util.Map<String, Object>) method.invoke(handler, "Required request part 'file is not present");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> blankPartDetails =
                (java.util.Map<String, Object>) method.invoke(handler, "Required request part '' is not present");

        assertTrue(nullDetails.isEmpty());
        assertTrue(unclosedQuoteDetails.isEmpty());
        assertTrue(blankPartDetails.isEmpty());
    }

    @Test
    void handleDataBufferLimitReturnsBadRequest() {
        ServerWebExchange exchange = exchange("trace-buffer", "request-buffer");

        ResponseEntity<ApiErrorResponse> response = handler.handleDataBufferLimit(
                new DataBufferLimitException("Exceeded limit"),
                exchange
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("File is too large.", body.message());
        assertEquals(5242880L, body.details().get("maxUploadSize"));
    }

    @Test
    void handleTypeMismatchSerializesNullParameterValue() {
        ServerWebExchange exchange = exchange("trace-5", "request-5");
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
                null,
                UUID.class,
                "jobId",
                null,
                new IllegalArgumentException("bad uuid")
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(mismatch, exchange);

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
        ServerWebExchange exchange = exchange("trace-6", "request-6");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"),
                exchange
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("NOT_FOUND", body.errorCode());
        assertEquals("job not found", body.message());
    }

    @Test
    void handleResponseStatusKeepsCustomCodeWithoutSeparatorAndGeneratesTraceId() {
        ServerWebExchange exchange = exchange(null, " ");
        HttpStatusCode customStatus = HttpStatusCode.valueOf(499);

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(customStatus, "custom reason"),
                exchange
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
    void handleResponseStatusUsesDefaultMessageWhenReasonMissing() {
        ServerWebExchange exchange = exchange("trace-null-reason", "request-null-reason");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE),
                exchange
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("SERVICE_UNAVAILABLE", body.errorCode());
        assertEquals("HTTP 503", body.message());
    }

    @Test
    void handleResponseStatusUsesDefaultMessageWhenReasonIsBlank() {
        ServerWebExchange exchange = exchange("trace-blank-reason", "request-blank-reason");

        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, " "),
                exchange
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.errorCode());
        assertEquals("HTTP 400", body.message());
    }

    @Test
    void handleUnexpectedReturnsInternalErrorPayload() {
        ServerWebExchange exchange = exchange("trace-7", "request-7");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("boom"),
                exchange
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
        ServerWebExchange exchange = exchange(null, null);

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("bad input"),
                exchange
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
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getId()).thenReturn(null);
        when(request.getURI()).thenReturn(null);

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("boom"),
                exchange
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.errorCode());
        assertNotNull(body.traceId());
        assertFalse(body.traceId().isBlank());
    }

    private ServerWebExchange exchange(String headerTraceId, String requestId) {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        HttpHeaders headers = new HttpHeaders();
        if (headerTraceId != null) {
            headers.add("X-Trace-Id", headerTraceId);
        }

        when(exchange.getRequest()).thenReturn(request);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getId()).thenReturn(requestId);
        when(request.getURI()).thenReturn(URI.create("https://example.test/test/path"));
        return exchange;
    }
}
