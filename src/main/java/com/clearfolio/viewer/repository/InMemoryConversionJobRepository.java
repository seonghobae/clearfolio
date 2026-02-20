package com.clearfolio.viewer.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Repository;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * In-memory repository implementation for conversion job persistence.
 */
@Repository
public class InMemoryConversionJobRepository implements ConversionJobRepository {

    private final ConcurrentHashMap<UUID, ConversionJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> jobsByContentHash = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public ConversionJob save(ConversionJob job) {
        jobs.put(job.getJobId(), job);
        if (job.getContentHash() != null && !job.getContentHash().isBlank()) {
            jobsByContentHash.putIfAbsent(job.getContentHash(), job.getJobId());
        }
        return job;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConversionJobRepository.FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
        String contentHash = candidate.getContentHash();
        if (contentHash == null || contentHash.isBlank()) {
            save(candidate);
            return new ConversionJobRepository.FindOrStoreResult(candidate, true);
        }

        AtomicBoolean created = new AtomicBoolean(false);
        AtomicReference<ConversionJob> canonical = new AtomicReference<>();
        jobsByContentHash.compute(
                contentHash,
                (key, existingJobId) -> {
                    if (existingJobId != null) {
                        ConversionJob existing = jobs.get(existingJobId);
                        if (existing != null) {
                            canonical.set(existing);
                            return existingJobId;
                        }
                    }

                    jobs.put(candidate.getJobId(), candidate);
                    created.set(true);
                    canonical.set(candidate);
                    return candidate.getJobId();
                }
        );

        return new ConversionJobRepository.FindOrStoreResult(canonical.get(), created.get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> findByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }

        UUID jobId = jobsByContentHash.get(contentHash);
        if (jobId == null) {
            return Optional.empty();
        }

        return findById(jobId);
    }
}
