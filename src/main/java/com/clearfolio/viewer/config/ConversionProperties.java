package com.clearfolio.viewer.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration values that control conversion validation and worker behavior.
 */
@ConfigurationProperties(prefix = "conversion")
public class ConversionProperties {

    private Set<String> blockedExtensions = new LinkedHashSet<>(Set.of("hwp", "hwpx"));
    private int workerThreads = 4;
    private int queueCapacity = 200;
    private int maxRetryAttempts = 3;
    private long retryInitialDelayMs = 500L;
    private long retryMaxDelayMs = 5_000L;
    private double retryBackoffMultiplier = 2.0;
    private long maxUploadSizeBytes = 5 * 1024 * 1024L;

    /**
     * Returns file extensions that are blocked from upload.
     *
     * @return blocked extension set
     */
    public Set<String> getBlockedExtensions() {
        return blockedExtensions;
    }

    /**
     * Sets file extensions that are blocked from upload.
     *
     * @param blockedExtensions blocked extension set
     */
    public void setBlockedExtensions(Set<String> blockedExtensions) {
        this.blockedExtensions = blockedExtensions;
    }

    /**
     * Returns the number of worker threads used for conversion execution.
     *
     * @return worker thread count
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * Sets the number of worker threads used for conversion execution.
     *
     * @param workerThreads worker thread count
     */
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /**
     * Returns the queue capacity for pending conversion work.
     *
     * @return queue capacity
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * Sets the queue capacity for pending conversion work.
     *
     * @param queueCapacity queue capacity
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * Returns the maximum number of retry attempts for a conversion job.
     *
     * @return max retry attempts
     */
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    /**
     * Sets the maximum number of retry attempts for a conversion job.
     *
     * @param maxRetryAttempts max retry attempts
     */
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = Math.max(1, maxRetryAttempts);
    }

    /**
     * Returns the initial retry delay in milliseconds.
     *
     * @return initial retry delay in milliseconds
     */
    public long getRetryInitialDelayMs() {
        return retryInitialDelayMs;
    }

    /**
     * Sets the initial retry delay in milliseconds.
     *
     * @param retryInitialDelayMs initial retry delay in milliseconds
     */
    public void setRetryInitialDelayMs(long retryInitialDelayMs) {
        this.retryInitialDelayMs = Math.max(0L, retryInitialDelayMs);
    }

    /**
     * Returns the maximum retry delay in milliseconds.
     *
     * @return max retry delay in milliseconds
     */
    public long getRetryMaxDelayMs() {
        return retryMaxDelayMs;
    }

    /**
     * Sets the maximum retry delay in milliseconds.
     *
     * @param retryMaxDelayMs max retry delay in milliseconds
     */
    public void setRetryMaxDelayMs(long retryMaxDelayMs) {
        this.retryMaxDelayMs = Math.max(0L, retryMaxDelayMs);
    }

    /**
     * Returns the retry backoff multiplier used for exponential delays.
     *
     * @return retry backoff multiplier
     */
    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    /**
     * Sets the retry backoff multiplier used for exponential delays.
     *
     * @param retryBackoffMultiplier retry backoff multiplier
     */
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = Math.max(1.0, retryBackoffMultiplier);
    }

    /**
     * Returns the maximum upload size in bytes.
     *
     * @return max upload size in bytes
     */
    public long getMaxUploadSizeBytes() {
        return maxUploadSizeBytes;
    }

    /**
     * Sets the maximum upload size in bytes.
     *
     * @param maxUploadSizeBytes max upload size in bytes
     */
    public void setMaxUploadSizeBytes(long maxUploadSizeBytes) {
        this.maxUploadSizeBytes = Math.max(1L, maxUploadSizeBytes);
    }
}
