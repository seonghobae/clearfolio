package com.clearfolio.viewer.repository;

import java.util.Optional;
import java.util.UUID;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Persistence abstraction for conversion jobs.
 */
public interface ConversionJobRepository {

    /**
     * Result of an atomic find-or-store operation by content hash.
     *
     * @param canonicalJob canonical stored conversion job
     * @param created true when the candidate was newly stored
     */
    record FindOrStoreResult(ConversionJob canonicalJob, boolean created) {
    }

    /**
     * Saves a conversion job.
     *
     * @param job conversion job to store
     * @return stored conversion job
     */
    ConversionJob save(ConversionJob job);

    /**
     * Finds a conversion job by identifier.
     *
     * @param jobId conversion job identifier
     * @return matching conversion job when found
     */
    Optional<ConversionJob> findById(UUID jobId);

    /**
     * Finds a conversion job by uploaded file content hash.
     *
     * @param contentHash uploaded file content hash
     * @return matching conversion job when found
     */
    Optional<ConversionJob> findByContentHash(String contentHash);

    /**
     * Stores a new job or returns the existing canonical job for the same hash.
     *
     * @param candidate candidate conversion job
     * @return canonical stored conversion job and whether the candidate was created
     */
    FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate);
}
