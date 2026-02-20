package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    @Test
    void normalizesNullDetailsToEmptyMap() {
        ApiErrorResponse response = new ApiErrorResponse("ERR", "message", "trace", null);

        assertTrue(response.details().isEmpty());
    }

    @Test
    void copiesDetailsDefensively() {
        Map<String, Object> details = new HashMap<>();
        details.put("key", "value");

        ApiErrorResponse response = new ApiErrorResponse("ERR", "message", "trace", details);
        details.put("key", "changed");

        assertEquals("value", response.details().get("key"));
        assertThrows(UnsupportedOperationException.class, () -> response.details().put("another", "x"));
    }
}
