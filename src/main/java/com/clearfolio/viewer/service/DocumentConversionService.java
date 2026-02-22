package com.clearfolio.viewer.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Application service for document conversion job submission and lookup.
 */
public interface DocumentConversionService {
    /**
     * Submits an uploaded file for conversion.
     *
     * @param file uploaded file
     * @return conversion job identifier
     */
    UUID submit(MultipartFile file);

    /**
     * Submits an uploaded file for conversion with optional policy-override metadata.
     *
     * @param file uploaded file
     * @param overrideRequest policy-override request headers
     * @return conversion job identifier
     */
    default UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        return submit(file);
    }

    /**
     * Retrieves a conversion job by identifier.
     *
     * @param jobId conversion job identifier
     * @return conversion job when found
     */
    Optional<ConversionJob> getJob(UUID jobId);

    /**
     * Retries a dead-lettered conversion job by moving it back to submitted state.
     *
     * @param jobId conversion job identifier
     * @param operatorId operator identifier that triggered the retry
     * @return retry outcome
     */
    RetryDeadLetterResult retryDeadLettered(UUID jobId, String operatorId);
}
