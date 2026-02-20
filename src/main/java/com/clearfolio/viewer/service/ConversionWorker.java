package com.clearfolio.viewer.service;

import java.util.UUID;

/**
 * Worker abstraction that schedules conversion jobs for background execution.
 */
public interface ConversionWorker {
    /**
     * Enqueues a job for asynchronous conversion processing.
     *
     * @param jobId conversion job identifier
     */
    void enqueue(UUID jobId);
}
