package com.clearfolio.viewer.service;

/**
 * Encapsulates optional policy-override headers sent with submit requests.
 */
public final class PolicyOverrideRequest {

    /** Header for toggling blocked-format override behavior. */
    public static final String POLICY_OVERRIDE_HEADER = "X-Clearfolio-Policy-Override";

    /** Header carrying approval token when override is enabled. */
    public static final String APPROVAL_TOKEN_HEADER = "X-Clearfolio-Approval-Token";

    /** Header carrying approver identifier when override is enabled. */
    public static final String APPROVER_ID_HEADER = "X-Clearfolio-Approver-Id";

    private static final PolicyOverrideRequest NONE = new PolicyOverrideRequest(null, null, null);

    private final String policyOverride;
    private final String approvalToken;
    private final String approverId;

    private PolicyOverrideRequest(String policyOverride, String approvalToken, String approverId) {
        this.policyOverride = policyOverride;
        this.approvalToken = approvalToken;
        this.approverId = approverId;
    }

    /**
     * Creates a request object from raw HTTP header values.
     *
     * @param policyOverride raw value of {@value #POLICY_OVERRIDE_HEADER}
     * @param approvalToken raw value of {@value #APPROVAL_TOKEN_HEADER}
     * @param approverId raw value of {@value #APPROVER_ID_HEADER}
     * @return request object carrying raw override header values
     */
    public static PolicyOverrideRequest of(String policyOverride, String approvalToken, String approverId) {
        if (policyOverride == null && approvalToken == null && approverId == null) {
            return NONE;
        }
        return new PolicyOverrideRequest(policyOverride, approvalToken, approverId);
    }

    /**
     * Returns an empty request with no override headers.
     *
     * @return empty request object
     */
    public static PolicyOverrideRequest none() {
        return NONE;
    }

    /**
     * Returns the raw override flag header value.
     *
     * @return raw override flag value
     */
    public String policyOverride() {
        return policyOverride;
    }

    /**
     * Returns the raw approval token header value.
     *
     * @return raw approval token value
     */
    public String approvalToken() {
        return approvalToken;
    }

    /**
     * Returns the raw approver identifier header value.
     *
     * @return raw approver identifier value
     */
    public String approverId() {
        return approverId;
    }

    @Override
    public String toString() {
        return "PolicyOverrideRequest{"
                + "policyOverride='" + policyOverride + '\''
                + ", approvalToken='[redacted]'"
                + ", approverId='" + approverId + '\''
                + '}';
    }
}
