package com.clearfolio.viewer.api;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API payload used for error responses.
 */
public record ApiErrorResponse(
        String errorCode,
        String message,
        String traceId,
        Map<String, Object> details
) {
    /**
     * Normalizes the details map to an immutable empty map when absent.
     */
    public ApiErrorResponse {
        Map<String, Object> normalized = details == null ? Collections.emptyMap() : Map.copyOf(details);
        details = normalized;
    }

    /**
     * Returns a backward-compatible alias for {@code errorCode}.
     *
     * @return API error code
     */
    @JsonProperty("code")
    public String getCode() {
        return errorCode;
    }
}
