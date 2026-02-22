package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PolicyOverrideRequestTest {

    @Test
    void noneReturnsSharedEmptyInstance() {
        PolicyOverrideRequest none = PolicyOverrideRequest.none();

        assertNotNull(none);
        assertEquals(null, none.policyOverride());
        assertEquals(null, none.approvalToken());
        assertEquals(null, none.approverId());
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

        assertTrue(request != PolicyOverrideRequest.none());
        assertEquals(null, request.policyOverride());
        assertEquals("token-123", request.approvalToken());
        assertEquals(null, request.approverId());
    }

    @Test
    void ofCreatesDistinctInstanceWhenOnlyApproverHeaderIsPresent() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of(null, null, "approver-1");

        assertTrue(request != PolicyOverrideRequest.none());
        assertEquals(null, request.policyOverride());
        assertEquals(null, request.approvalToken());
        assertEquals("approver-1", request.approverId());
    }

    @Test
    void toStringRedactsApprovalToken() {
        PolicyOverrideRequest request = PolicyOverrideRequest.of("true", "secret-token", "approver-1");

        String rendered = request.toString();

        assertTrue(rendered.contains("approvalToken='[redacted]'"));
        assertTrue(!rendered.contains("secret-token"));
    }
}
