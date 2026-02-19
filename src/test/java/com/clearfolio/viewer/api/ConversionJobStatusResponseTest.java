package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

class ConversionJobStatusResponseTest {

    @Test
    void mapsRetryMetadataIntoStatusResponse() {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L,
                5
        );
        Instant retryAt = Instant.now().plusSeconds(30);

        job.markProcessing("conversion started");
        job.markRetryScheduled("scheduled", retryAt);

        ConversionJobStatusResponse response = ConversionJobStatusResponse.from(job);

        assertEquals(1, response.attemptCount());
        assertEquals(5, response.maxAttempts());
        assertEquals(retryAt, response.retryAt());
        assertEquals(false, response.deadLettered());
    }
}
