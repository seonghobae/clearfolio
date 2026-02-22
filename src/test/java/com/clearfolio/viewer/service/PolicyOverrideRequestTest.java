package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PolicyOverrideRequestTest {

    @Test
    void noneReturnsSharedEmptyInstance() {
        PolicyOverrideRequest none = PolicyOverrideRequest.none();

        assertNotNull(none);
        assertNull(none.policyOverride());
        assertNull(none.approvalToken());
        assertNull(none.approverId());
        assertSame(none, PolicyOverrideRequest.of(null, null, null));
    }

    @Test
    void ofRetainsProvidedHeaderValues() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true", "token-123", "approver-1");

        assertEquals("true", request.policyOverride());
        assertEquals("token-123", request.approvalToken());
        assertEquals("approver-1", request.approverId());
    }

    @Test
    void ofCreatesDistinctInstanceWhenOnlyPartialHeadersArePresent() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(null, "token-123", null);

        assertNotSame(PolicyOverrideRequest.none(), request);
        assertNull(request.policyOverride());
        assertEquals("token-123", request.approvalToken());
        assertNull(request.approverId());
    }

    @Test
    void ofCreatesDistinctInstanceWhenOnlyApproverHeaderIsPresent() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(null, null, "approver-1");

        assertNotSame(PolicyOverrideRequest.none(), request);
        assertNull(request.policyOverride());
        assertNull(request.approvalToken());
        assertEquals("approver-1", request.approverId());
    }

    @Test
    void toStringRedactsApprovalToken() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true", "secret-token", "approver-1");

        String rendered = request.toString();

        assertTrue(rendered.contains("approvalToken='[redacted]'"));
        assertTrue(!rendered.contains("secret-token"));
    }

    @Test
    void toStringNormalizesControlCharactersInPrintableHeaders() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true\n", "secret-token", "approver\r\n1\t");

        String rendered = request.toString();

        assertTrue(rendered.contains("policyOverride='true_'"));
        assertTrue(rendered.contains("approverId='approver__1_'"));
    }

    @Test
    void toStringHandlesNullPrintableHeaders() {
        String rendered = PolicyOverrideRequest.none().toString();

        assertTrue(rendered.contains("policyOverride='null'"));
        assertTrue(rendered.contains("approverId='null'"));
    }
}
