package com.clearfolio.viewer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;

/**
 * Default background worker that executes conversion jobs with retry backoff.
 */
@Component
public class DefaultConversionWorker implements ConversionWorker {

    private static final long SIMULATED_WORK_TIME_MS = 25L;
    private static final long MIN_INITIAL_RETRY_DELAY_MS = 250L;

    private final ConversionJobRepository repository;
    private final Executor conversionExecutor;
    private final long retryInitialDelayMs;
    private final long retryMaxDelayMs;
    private final double retryBackoffMultiplier;
    private final Function<UUID, String> conversionTask;

    /**
     * Creates a conversion worker using the default conversion task implementation.
     *
     * @param repository conversion job repository
     * @param conversionExecutor asynchronous conversion executor
     * @param conversionProperties conversion configuration values
     */
    @Autowired
    public DefaultConversionWorker(
            ConversionJobRepository repository,
            Executor conversionExecutor,
            com.clearfolio.viewer.config.ConversionProperties conversionProperties) {
        this.repository = repository;
        this.conversionExecutor = conversionExecutor;
        this.retryInitialDelayMs = Math.max(
                MIN_INITIAL_RETRY_DELAY_MS,
                conversionProperties.getRetryInitialDelayMs()
        );
        this.retryMaxDelayMs = Math.max(
                retryInitialDelayMs,
                conversionProperties.getRetryMaxDelayMs()
        );
        this.retryBackoffMultiplier = conversionProperties.getRetryBackoffMultiplier();
        this.conversionTask = this::performDefaultConversion;
    }

    DefaultConversionWorker(
            ConversionJobRepository repository,
            Executor conversionExecutor,
            com.clearfolio.viewer.config.ConversionProperties conversionProperties,
            Function<UUID, String> conversionTask) {
        this.repository = repository;
        this.conversionExecutor = conversionExecutor;
        this.retryInitialDelayMs = Math.max(
                MIN_INITIAL_RETRY_DELAY_MS,
                conversionProperties.getRetryInitialDelayMs()
        );
        this.retryMaxDelayMs = Math.max(
                retryInitialDelayMs,
                conversionProperties.getRetryMaxDelayMs()
        );
        this.retryBackoffMultiplier = conversionProperties.getRetryBackoffMultiplier();
        this.conversionTask = conversionTask;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enqueue(UUID jobId) {
        try {
            CompletableFuture.runAsync(() -> process(jobId), conversionExecutor);
        } catch (RejectedExecutionException ex) {
            repository.findById(jobId)
                    .ifPresent(job -> job.markDeadLettered("worker queue saturated"));
        }
    }

    private void process(UUID jobId) {
        Instant now = Instant.now();
        repository.findById(jobId).ifPresent(job -> {
            if (!job.isReadyForProcessing(now)) {
                Instant retryAt = job.getRetryAt();
                if (retryAt != null && now.isBefore(retryAt)) {
                    scheduleRetry(jobId, retryAt);
                }
                return;
            }

            if (!job.markProcessing("conversion started")) {
                return;
            }

            try {
                String convertedResourcePath = conversionTask.apply(jobId);
                job.markSucceeded(convertedResourcePath, "conversion completed");
            } catch (RuntimeException ex) {
                onFailure(job, "conversion failed: " + ex.getMessage());
            }
        });
    }

    private void onFailure(ConversionJob job, String reason) {
        if (job.canRetry()) {
            long retryDelayMs = computeRetryDelay(job.getAttemptCount());
            Instant retryAt = Instant.now().plusMillis(retryDelayMs);
            job.markRetryScheduled("retry scheduled in " + retryDelayMs + "ms", retryAt);
            scheduleRetry(job.getJobId(), retryAt);
            return;
        }

        job.markDeadLettered(reason);
    }

    private long computeRetryDelay(int attemptCount) {
        if (attemptCount <= 1) {
            return retryInitialDelayMs;
        }

        double power = Math.pow(retryBackoffMultiplier, Math.max(0, attemptCount - 1));
        long exponential = Math.round(retryInitialDelayMs * power);
        long bounded = Math.max(retryInitialDelayMs, exponential);
        return Math.min(bounded, retryMaxDelayMs);
    }

    private void scheduleRetry(UUID jobId, Instant retryAt) {
        long delayMs = Duration.between(Instant.now(), retryAt).toMillis();
        if (delayMs <= 0) {
            enqueue(jobId);
            return;
        }

        CompletableFuture.runAsync(
                () -> process(jobId),
                CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, conversionExecutor)
        );
    }

    private String performDefaultConversion(UUID jobId) {
        try {
            Thread.sleep(SIMULATED_WORK_TIME_MS);
            return "/artifacts/" + jobId + ".pdf";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("conversion interrupted", ex);
        }
    }
}
