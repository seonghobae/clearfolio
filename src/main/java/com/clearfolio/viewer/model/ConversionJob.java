package com.clearfolio.viewer.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable domain model that tracks conversion lifecycle and retry metadata.
 */
public class ConversionJob {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final UUID jobId;
    private final String originalFileName;
    private final String contentType;
    private final String contentHash;
    private final long fileSize;
    private final Instant createdAt;

    private volatile ConversionJobStatus status;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String statusMessage;
    private volatile String convertedResourcePath;

    private volatile int attemptCount;
    private final int maxAttempts;
    private volatile Instant retryAt;
    private volatile boolean deadLettered;

    /**
     * Creates a conversion job with the default retry attempt limit.
     *
     * @param jobId job identifier
     * @param originalFileName original uploaded file name
     * @param contentType uploaded file content type
     * @param contentHash uploaded file content hash
     * @param fileSize uploaded file size in bytes
     */
    public ConversionJob(
            UUID jobId,
            String originalFileName,
            String contentType,
            String contentHash,
            long fileSize
    ) {
        this(jobId, originalFileName, contentType, contentHash, fileSize, DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * Creates a conversion job with an explicit retry attempt limit.
     *
     * @param jobId job identifier
     * @param originalFileName original uploaded file name
     * @param contentType uploaded file content type
     * @param contentHash uploaded file content hash
     * @param fileSize uploaded file size in bytes
     * @param maxAttempts maximum retry attempts
     */
    public ConversionJob(
            UUID jobId,
            String originalFileName,
            String contentType,
            String contentHash,
            long fileSize,
            int maxAttempts
    ) {
        this.jobId = jobId;
        this.originalFileName = sanitize(originalFileName);
        this.contentType = sanitize(contentType);
        this.contentHash = contentHash;
        this.fileSize = fileSize;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.createdAt = Instant.now();
        this.status = ConversionJobStatus.SUBMITTED;
        this.statusMessage = "queued";
        this.attemptCount = 0;
        this.deadLettered = false;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\u0000", "");
    }

    /**
     * Returns the unique identifier of this conversion job.
     *
     * @return job identifier
     */
    public UUID getJobId() {
        return jobId;
    }

    /**
     * Returns the original file name submitted for conversion.
     *
     * @return original file name
     */
    public String getOriginalFileName() {
        return originalFileName;
    }

    /**
     * Returns the content type reported for the uploaded file.
     *
     * @return file content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the content hash of the uploaded file.
     *
     * @return content hash
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * Returns the uploaded file size in bytes.
     *
     * @return file size in bytes
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Returns the instant when this job was created.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return current conversion status
     */
    public ConversionJobStatus getStatus() {
        return status;
    }

    /**
     * Returns the instant when processing started.
     *
     * @return processing start timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Returns the instant when processing finished.
     *
     * @return processing completion timestamp
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * Returns the current status message.
     *
     * @return status message
     */
    public String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Returns the path to the converted resource when available.
     *
     * @return converted resource path
     */
    public String getConvertedResourcePath() {
        return convertedResourcePath;
    }

    /**
     * Returns how many processing attempts have been executed.
     *
     * @return attempt count
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Returns the maximum allowed processing attempts.
     *
     * @return max attempt count
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Returns the next scheduled retry instant, if any.
     *
     * @return next retry timestamp
     */
    public Instant getRetryAt() {
        return retryAt;
    }

    /**
     * Returns whether this job has been dead-lettered.
     *
     * @return true when retries are exhausted and job is dead-lettered
     */
    public boolean isDeadLettered() {
        return deadLettered;
    }

    /**
     * Returns whether the job can be processed at the supplied instant.
     *
     * @param now evaluation timestamp
     * @return true when the job is submitted, not dead-lettered, and retry delay elapsed
     */
    public synchronized boolean isReadyForProcessing(Instant now) {
        if (status != ConversionJobStatus.SUBMITTED || deadLettered) {
            return false;
        }

        return retryAt == null || !now.isBefore(retryAt);
    }

    /**
     * Moves the job into processing state when transition preconditions are met.
     *
     * @param message status message for processing start
     * @return true when state transition succeeded
     */
    public synchronized boolean markProcessing(String message) {
        if (status != ConversionJobStatus.SUBMITTED || deadLettered) {
            return false;
        }

        if (attemptCount >= maxAttempts) {
            return false;
        }

        Instant now = Instant.now();
        if (retryAt != null && retryAt.isAfter(now)) {
            return false;
        }

        this.status = ConversionJobStatus.PROCESSING;
        this.attemptCount++;
        this.startedAt = now;
        this.completedAt = null;
        this.retryAt = null;
        this.deadLettered = false;
        this.statusMessage = sanitize(message);
        return true;
    }

    /**
     * Returns whether another retry attempt is allowed.
     *
     * @return true when attempts remain
     */
    public synchronized boolean canRetry() {
        return attemptCount < maxAttempts;
    }

    /**
     * Moves the job back to submitted state with a scheduled retry time.
     *
     * @param message retry scheduling message
     * @param retryAt instant when next retry should run
     */
    public synchronized void markRetryScheduled(String message, Instant retryAt) {
        this.status = ConversionJobStatus.SUBMITTED;
        this.startedAt = null;
        this.completedAt = null;
        this.retryAt = retryAt;
        this.deadLettered = false;
        this.statusMessage = sanitize(message);
    }

    /**
     * Re-enables a dead-lettered job by resetting it to submitted state.
     *
     * @param operatorId operator identifier that triggered the retry
     * @return true when the job was dead-lettered and transitioned for retry
     */
    public synchronized boolean retryDeadLetteredToSubmitted(String operatorId) {
        if (status != ConversionJobStatus.FAILED || !deadLettered) {
            return false;
        }

        String normalizedOperatorId = sanitize(operatorId);
        String message = (normalizedOperatorId == null || normalizedOperatorId.isBlank())
                ? "operator retry queued"
                : "operator retry queued by " + normalizedOperatorId;

        this.status = ConversionJobStatus.SUBMITTED;
        this.startedAt = null;
        this.completedAt = null;
        this.retryAt = null;
        this.deadLettered = false;
        this.attemptCount = 0;
        this.convertedResourcePath = null;
        this.statusMessage = message;
        return true;
    }

    /**
     * Marks the job as successfully converted.
     *
     * @param convertedResourcePath output resource path
     * @param message completion message
     */
    public synchronized void markSucceeded(String convertedResourcePath, String message) {
        this.status = ConversionJobStatus.SUCCEEDED;
        this.completedAt = Instant.now();
        this.convertedResourcePath = sanitize(convertedResourcePath);
        this.statusMessage = sanitize(message);
        this.deadLettered = false;
        this.retryAt = null;
    }

    /**
     * Marks the job as failed without dead-lettering it.
     *
     * @param message failure message
     */
    public synchronized void markFailed(String message) {
        this.status = ConversionJobStatus.FAILED;
        this.completedAt = Instant.now();
        this.statusMessage = sanitize(message);
        this.retryAt = null;
    }

    /**
     * Marks the job as failed and dead-lettered after retry exhaustion.
     *
     * @param message dead-letter reason
     */
    public synchronized void markDeadLettered(String message) {
        this.status = ConversionJobStatus.FAILED;
        this.completedAt = Instant.now();
        this.retryAt = null;
        this.deadLettered = true;
        this.statusMessage = sanitize(message);
    }
}
