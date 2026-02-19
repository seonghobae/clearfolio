package com.clearfolio.viewer.api;

import java.util.UUID;

/**
 * API payload returned when a conversion request is accepted.
 */
public record SubmitConversionResponse(UUID jobId, String status, String statusUrl) {

    /**
     * Builds the standard accepted response for a new conversion job.
     *
     * @param jobId accepted conversion job identifier
     * @return accepted conversion response payload
     */
    public static SubmitConversionResponse accepted(UUID jobId) {
        return new SubmitConversionResponse(jobId, "ACCEPTED", "/api/v1/convert/jobs/" + jobId);
    }
}
