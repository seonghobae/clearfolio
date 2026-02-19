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
     * Retrieves a conversion job by identifier.
     *
     * @param jobId conversion job identifier
     * @return conversion job when found
     */
    Optional<ConversionJob> getJob(UUID jobId);
}
