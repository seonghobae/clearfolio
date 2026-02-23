package com.clearfolio.viewer.service;

/**
 * Result of attempting to retry a dead-lettered conversion job.
 */
public enum RetryDeadLetterResult {
    /**
     * No job exists for the requested identifier.
     */
    NOT_FOUND,

    /**
     * Job exists but is not eligible for dead-letter retry.
     */
    NOT_ELIGIBLE,

    /**
     * Retry was accepted and the job was re-enqueued.
     */
    ACCEPTED
}
