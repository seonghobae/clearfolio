package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;

class DefaultConversionWorkerTest {

    @Test
    void computeRetryDelayUsesExponentialBackoffAndMaxBound() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setRetryInitialDelayMs(100L);
        conversionProperties.setRetryMaxDelayMs(600L);
        conversionProperties.setRetryBackoffMultiplier(2.0);

        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                conversionProperties,
                id -> "/artifacts/" + id + ".pdf"
        );

        assertEquals(250L, invokeComputeRetryDelay(worker, 1));
        assertEquals(500L, invokeComputeRetryDelay(worker, 2));
        assertEquals(600L, invokeComputeRetryDelay(worker, 5));
    }

    @Test
    void workerRetriesFailedJobUntilDeadLettering() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ConversionJobRepository repository = new InMemoryConversionJobRepository();
            ConversionProperties conversionProperties = new ConversionProperties();
            conversionProperties.setMaxRetryAttempts(2);
            conversionProperties.setRetryInitialDelayMs(10L);
            conversionProperties.setRetryMaxDelayMs(10L);
            conversionProperties.setRetryBackoffMultiplier(1.0);

            UUID jobId = UUID.randomUUID();
            ConversionJob job = new ConversionJob(
                    jobId,
                    "report.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "abc",
                    12L,
                    2
            );
            repository.save(job);

            AtomicInteger attempts = new AtomicInteger();
            DefaultConversionWorker worker = new DefaultConversionWorker(
                    repository,
                    executor,
                    conversionProperties,
                    id -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("boom");
                    }
            );

            worker.enqueue(jobId);

            await(() -> job.isDeadLettered() && attempts.get() >= 2, 5_000);

            assertEquals(2, attempts.get());
            assertEquals(2, job.getAttemptCount());
            assertEquals(2, job.getMaxAttempts());
            assertEquals(ConversionJobStatus.FAILED, job.getStatus());
            assertTrue(job.isDeadLettered());
            assertEquals("conversion failed: boom", job.getStatusMessage());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void workerRetriesThenCompletesWhenConversionSucceeds() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ConversionJobRepository repository = new InMemoryConversionJobRepository();
            ConversionProperties conversionProperties = new ConversionProperties();
            conversionProperties.setMaxRetryAttempts(3);
            conversionProperties.setRetryInitialDelayMs(10L);
            conversionProperties.setRetryMaxDelayMs(10L);
            conversionProperties.setRetryBackoffMultiplier(1.0);

            UUID jobId = UUID.randomUUID();
            ConversionJob job = new ConversionJob(
                    jobId,
                    "report.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "abc",
                    12L,
                    3
            );
            repository.save(job);

            AtomicInteger attempts = new AtomicInteger();
            DefaultConversionWorker worker = new DefaultConversionWorker(
                    repository,
                    executor,
                    conversionProperties,
                    id -> {
                        if (attempts.getAndIncrement() == 0) {
                            throw new IllegalStateException("boom");
                        }
                        return "/artifacts/" + id + ".pdf";
                    }
            );

            worker.enqueue(jobId);

            await(() -> job.getStatus() == ConversionJobStatus.SUCCEEDED && attempts.get() >= 2, 5_000);

            assertEquals(2, attempts.get());
            assertEquals(2, job.getAttemptCount());
            assertFalse(job.isDeadLettered());
            assertNull(job.getRetryAt());
            assertEquals("/artifacts/" + jobId + ".pdf", job.getConvertedResourcePath());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void workerReschedulesWhenRetryAtIsInTheFuture() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ConversionJobRepository repository = new InMemoryConversionJobRepository();
            ConversionProperties conversionProperties = new ConversionProperties();
            conversionProperties.setMaxRetryAttempts(2);
            conversionProperties.setRetryInitialDelayMs(10L);
            conversionProperties.setRetryMaxDelayMs(10L);
            conversionProperties.setRetryBackoffMultiplier(1.0);

            UUID jobId = UUID.randomUUID();
            ConversionJob job = new ConversionJob(
                    jobId,
                    "report.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "abc",
                    12L,
                    2
            );
            job.markRetryScheduled("retry in 500ms", Instant.now().plusMillis(500));
            repository.save(job);

            AtomicInteger attempts = new AtomicInteger();
            DefaultConversionWorker worker = new DefaultConversionWorker(
                    repository,
                    executor,
                    conversionProperties,
                    id -> {
                        attempts.incrementAndGet();
                        return "/artifacts/" + id + ".pdf";
                    }
            );

            worker.enqueue(jobId);

            Thread.sleep(150);
            assertEquals(0, attempts.get());

            await(() -> attempts.get() >= 1, 3_000);

            assertEquals(1, attempts.get());
            assertEquals(1, job.getAttemptCount());
            assertEquals(ConversionJobStatus.SUCCEEDED, job.getStatus());
            assertEquals("/artifacts/" + jobId + ".pdf", job.getConvertedResourcePath());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void workerDeadLettersJobWhenExecutorIsRejected() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };

        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L,
                3
        );
        repository.save(job);

        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                rejectingExecutor,
                conversionProperties
        );

        worker.enqueue(jobId);

        assertEquals(ConversionJobStatus.FAILED, job.getStatus());
        assertTrue(job.isDeadLettered());
        assertEquals("worker queue saturated", job.getStatusMessage());
    }

    @Test
    void workerDeadLettersProcessingJobWhenExecutorIsRejected() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };

        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc-processing",
                12L,
                3
        );
        assertTrue(job.markProcessing("already processing"));
        repository.save(job);

        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                rejectingExecutor,
                conversionProperties
        );

        worker.enqueue(jobId);

        assertEquals(ConversionJobStatus.FAILED, job.getStatus());
        assertTrue(job.isDeadLettered());
        assertEquals("worker queue saturated", job.getStatusMessage());
    }

    @Test
    void workerIgnoresMissingJobWhenExecutorRejectsEnqueue() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };

        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                rejectingExecutor,
                new ConversionProperties()
        );

        assertDoesNotThrow(() -> worker.enqueue(UUID.randomUUID()));
    }

    @Test
    void scheduleRetryDeadLettersJobWhenExecutorRejectsDelayedExecution() throws Exception {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };

        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "hash-schedule-reject",
                10L,
                3
        );
        repository.save(job);

        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                rejectingExecutor,
                conversionProperties,
                id -> "/artifacts/" + id + ".pdf"
        );

        invokeScheduleRetry(worker, jobId, Instant.now().plusMillis(10));
        await(job::isDeadLettered, 1_000);

        assertEquals(ConversionJobStatus.FAILED, job.getStatus());
        assertEquals("worker queue saturated", job.getStatusMessage());
    }

    @Test
    void scheduleRetryRejectionDoesNotDowngradeAlreadySucceededJob() throws Exception {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };

        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "hash-already-succeeded",
                10L,
                3
        );
        job.markSucceeded("/artifacts/" + jobId + ".pdf", "already done");
        repository.save(job);

        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                rejectingExecutor,
                conversionProperties,
                id -> "/artifacts/" + id + ".pdf"
        );

        invokeScheduleRetry(worker, jobId, Instant.now().plusMillis(10));
        Thread.sleep(75);

        assertEquals(ConversionJobStatus.SUCCEEDED, job.getStatus());
        assertFalse(job.isDeadLettered());
        assertEquals("already done", job.getStatusMessage());
    }

    @Test
    void scheduleRetryImmediatelyRequeuesWhenRetryTimeAlreadyPassed() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "hash-requeue",
                10L,
                2
        );
        repository.save(job);

        AtomicInteger attempts = new AtomicInteger();
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                conversionProperties,
                id -> {
                    attempts.incrementAndGet();
                    return "/artifacts/" + id + ".pdf";
                }
        );

        invokeScheduleRetry(worker, jobId, Instant.now().minusMillis(1));

        assertEquals(1, attempts.get());
        assertEquals(1, job.getAttemptCount());
        assertEquals(ConversionJobStatus.SUCCEEDED, job.getStatus());
    }

    @Test
    void workerSkipsWhenJobIsAlreadyProcessingWithoutRetrySchedule() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "hash-processing",
                10L,
                2
        );
        job.markProcessing("already started");
        repository.save(job);

        AtomicInteger attempts = new AtomicInteger();
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                conversionProperties,
                id -> {
                    attempts.incrementAndGet();
                    return "/artifacts/" + id + ".pdf";
                }
        );

        worker.enqueue(jobId);

        assertEquals(0, attempts.get());
        assertEquals(ConversionJobStatus.PROCESSING, job.getStatus());
    }

    @Test
    void workerDoesNotRescheduleWhenRetryAtIsNotInFuture() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "hash-not-future",
                10L,
                2
        ) {
            private final Instant retryAt = Instant.now().minusMillis(1);

            @Override
            public synchronized boolean isReadyForProcessing(Instant now) {
                return false;
            }

            @Override
            public synchronized Instant getRetryAt() {
                return retryAt;
            }
        };
        repository.save(job);

        AtomicInteger attempts = new AtomicInteger();
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                conversionProperties,
                id -> {
                    attempts.incrementAndGet();
                    return "/artifacts/" + id + ".pdf";
                }
        );

        worker.enqueue(jobId);

        assertEquals(0, attempts.get());
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
    }

    @Test
    void workerReturnsWhenJobCannotTransitionToProcessing() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        ConversionProperties conversionProperties = new ConversionProperties();

        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "hash-transition",
                10L,
                2
        ) {
            @Override
            public synchronized boolean isReadyForProcessing(Instant now) {
                return true;
            }

            @Override
            public synchronized boolean markProcessing(String message) {
                return false;
            }
        };
        repository.save(job);

        AtomicInteger attempts = new AtomicInteger();
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                conversionProperties,
                id -> {
                    attempts.incrementAndGet();
                    return "/artifacts/" + id + ".pdf";
                }
        );

        worker.enqueue(jobId);

        assertEquals(0, attempts.get());
        assertEquals(ConversionJobStatus.SUBMITTED, job.getStatus());
    }

    @Test
    void performDefaultConversionThrowsWhenThreadIsInterrupted() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        DefaultConversionWorker worker = new DefaultConversionWorker(
                repository,
                Runnable::run,
                new ConversionProperties()
        );

        Thread.currentThread().interrupt();
        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> invokePerformDefaultConversion(worker, UUID.randomUUID())
            );
            assertEquals("conversion interrupted", error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void await(java.util.function.Supplier<Boolean> condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(25);
        }

        throw new AssertionError("condition not met within timeout");
    }

    private long invokeComputeRetryDelay(DefaultConversionWorker worker, int attemptCount) throws Exception {
        Method method = DefaultConversionWorker.class.getDeclaredMethod("computeRetryDelay", int.class);
        method.setAccessible(true);
        return (Long) method.invoke(worker, attemptCount);
    }

    private void invokeScheduleRetry(DefaultConversionWorker worker, UUID jobId, Instant retryAt) throws Exception {
        Method method = DefaultConversionWorker.class.getDeclaredMethod("scheduleRetry", UUID.class, Instant.class);
        method.setAccessible(true);
        method.invoke(worker, jobId, retryAt);
    }

    private String invokePerformDefaultConversion(DefaultConversionWorker worker, UUID jobId) {
        try {
            Method method = DefaultConversionWorker.class.getDeclaredMethod("performDefaultConversion", UUID.class);
            method.setAccessible(true);
            return (String) method.invoke(worker, jobId);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
