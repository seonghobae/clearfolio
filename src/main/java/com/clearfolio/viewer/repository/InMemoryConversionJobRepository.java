package com.clearfolio.viewer.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    public ConversionJob findOrStoreByContentHash(ConversionJob candidate) {
        String contentHash = candidate.getContentHash();
        if (contentHash == null || contentHash.isBlank()) {
            save(candidate);
            return candidate;
        }

        UUID winnerJobId = jobsByContentHash.computeIfAbsent(
                contentHash,
                key -> {
                    jobs.put(candidate.getJobId(), candidate);
                    return candidate.getJobId();
                }
        );

        ConversionJob winner = jobs.get(winnerJobId);
        if (winner == null) {
            jobs.put(candidate.getJobId(), candidate);
            jobsByContentHash.put(contentHash, candidate.getJobId());
            return candidate;
        }

        return winner;
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
