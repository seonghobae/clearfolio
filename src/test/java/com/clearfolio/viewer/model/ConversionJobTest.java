package com.clearfolio.viewer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConversionJobTest {

    @Test
    void sanitizesNullBytesAcrossJobMetadata() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name\u0000.docx",
                "application\u0000/pdf",
                "abc",
                42L
        );

        assertEquals("name.docx", job.getOriginalFileName());
        assertEquals("application/pdf", job.getContentType());

        job.markProcessing("processing\u0000 started");
        assertEquals("processing started", job.getStatusMessage());

        job.markSucceeded("out\u0000/result.pdf", "done\u0000");
        assertEquals("out/result.pdf", job.getConvertedResourcePath());
        assertEquals("done", job.getStatusMessage());
        assertEquals("SUCCEEDED", job.getStatus().name());

        job.markFailed("failed\u0000");
        assertEquals("failed", job.getStatusMessage());
        assertEquals("FAILED", job.getStatus().name());
    }

    @Test
    void clampsMaxAttemptsToOneWhenZeroIsConfigured() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name.docx",
                "application/pdf",
                "hash-clamp",
                1L,
                0
        );

        assertEquals(1, job.getMaxAttempts());
        assertTrue(job.canRetry());
    }

    @Test
    void markProcessingRejectsWhenRetryWindowIsInFuture() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name.docx",
                "application/pdf",
                "hash-future",
                1L,
                2
        );
        job.markRetryScheduled("retry later", Instant.now().plusSeconds(1));

        assertFalse(job.isReadyForProcessing(Instant.now()));
        assertFalse(job.markProcessing("start now"));
        assertEquals(0, job.getAttemptCount());
    }

    @Test
    void markProcessingRejectsWhenAttemptsAreExhausted() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name.docx",
                "application/pdf",
                "hash-exhausted",
                1L,
                1
        );

        assertTrue(job.markProcessing("first"));
        job.markRetryScheduled("retry immediately", Instant.now().minusMillis(1));

        assertFalse(job.markProcessing("second"));
        assertEquals(1, job.getAttemptCount());
    }

    @Test
    void markRetryScheduledClearsStaleProcessingAndCompletionTimestamps() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name.docx",
                "application/pdf",
                "hash-retry-reset",
                1L,
                3
        );

        assertTrue(job.markProcessing("first"));
        assertNotNull(job.getStartedAt());
        job.markFailed("first failed");
        assertNotNull(job.getCompletedAt());

        job.markRetryScheduled("retry soon", Instant.now().plusMillis(50));

        assertNull(job.getStartedAt());
        assertNull(job.getCompletedAt());
        assertFalse(job.isDeadLettered());
    }

    @Test
    void transitionsAcrossRetryDeadLetterAndSuccessResetFlags() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name.docx",
                "application/pdf",
                "hash-edge",
                1L,
                1
        );

        assertTrue(job.markProcessing("start"));
        assertFalse(job.canRetry());

        Instant pastRetry = Instant.now().minusMillis(1);
        job.markRetryScheduled("retry now", pastRetry);
        assertTrue(job.isReadyForProcessing(Instant.now()));

        job.markDeadLettered("dead");
        assertEquals("FAILED", job.getStatus().name());
        assertTrue(job.isDeadLettered());
        assertNotNull(job.getCompletedAt());
        assertFalse(job.isReadyForProcessing(Instant.now()));
        assertFalse(job.markProcessing("retry dead-lettered"));

        job.markSucceeded("/artifacts/final.pdf", "done");
        assertEquals("SUCCEEDED", job.getStatus().name());
        assertFalse(job.isDeadLettered());
        assertNull(job.getRetryAt());
    }

    @Test
    void supportsNullMetadataAndExposesFileSize() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                null,
                null,
                "hash-null-metadata",
                99L
        );

        assertNull(job.getOriginalFileName());
        assertNull(job.getContentType());
        assertEquals(99L, job.getFileSize());
    }

    @Test
    void rejectsReadinessAndProcessingWhenDeadLetteredWhileSubmitted() throws Exception {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "name.docx",
                "application/pdf",
                "hash-dead-lettered",
                1L,
                2
        );

        setField(job, "status", ConversionJobStatus.SUBMITTED);
        setField(job, "deadLettered", true);

        assertFalse(job.isReadyForProcessing(Instant.now()));
        assertFalse(job.markProcessing("start"));
        assertEquals(0, job.getAttemptCount());
    }

    private void setField(ConversionJob job, String fieldName, Object value) throws Exception {
        Field field = ConversionJob.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(job, value);
    }
}
