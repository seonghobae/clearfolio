package com.clearfolio.viewer.api;

import java.time.Instant;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * API payload describing the current state of a conversion job.
 */
public record ConversionJobStatusResponse(
        UUID jobId,
        String fileName,
        String status,
        String message,
        String convertedResourcePath,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        int attemptCount,
        int maxAttempts,
        Instant retryAt,
        boolean deadLettered
) {

    /**
     * Creates a response payload from the domain conversion job model.
     *
     * @param job conversion job model
     * @return mapped API response
     */
    public static ConversionJobStatusResponse from(ConversionJob job) {
        return new ConversionJobStatusResponse(
                job.getJobId(),
                job.getOriginalFileName(),
                job.getStatus().name(),
                job.getStatusMessage(),
                job.getConvertedResourcePath(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getRetryAt(),
                job.isDeadLettered()
        );
    }
}
