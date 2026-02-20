package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.InMemoryConversionJobRepository;
import com.clearfolio.viewer.repository.ConversionJobRepository;

class DefaultDocumentConversionServiceTest {

    @Test
    void returnsSameJobIdForDuplicatePayloads() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID first = service.submit(file);
        UUID second = service.submit(file);

        assertEquals(first, second);
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void returnsDifferentJobIdsForDifferentPayloads() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new ConversionProperties()
        );

        MockMultipartFile firstFile = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );
        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "different-content".getBytes()
        );

        UUID first = service.submit(firstFile);
        UUID second = service.submit(secondFile);

        assertNotEquals(first, second);
        assertEquals(2, worker.enqueuedCount());
    }

    @Test
    void returnsSameJobForConcurrentDuplicatePayloads() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer-concurrent".getBytes()
        );

        ExecutorService executor = Executors.newFixedThreadPool(8);
        Callable<UUID> task = () -> service.submit(file);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(task));
        }

        Set<UUID> jobIds = new HashSet<>();
        for (Future<UUID> future : futures) {
            try {
                jobIds.add(future.get());
            } catch (ExecutionException ex) {
                throw new AssertionError("worker invocation failed", ex);
            }
        }
        executor.shutdownNow();
        assertEquals(1, jobIds.size());
        assertEquals(1, worker.enqueuedCount());
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void submitEnqueuesWhenRepositoryReportsCreatedWithDifferentCanonicalJobId() {
        RecordingConversionWorker worker = new RecordingConversionWorker();
        UUID canonicalId = UUID.randomUUID();
        ConversionJob canonical = new ConversionJob(
                canonicalId,
                "canonical.docx",
                "application/octet-stream",
                "canonical-hash",
                10L,
                3
        );
        ConversionJobRepository repository = new FindOrStoreOnlyRepository(
                candidate -> new ConversionJobRepository.FindOrStoreResult(canonical, true)
        );

        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID submitted = service.submit(file);

        assertEquals(canonicalId, submitted);
        assertEquals(1, worker.enqueuedCount());
    }

    @Test
    void submitSkipsEnqueueWhenRepositoryReportsExistingEvenForCandidateCanonical() {
        RecordingConversionWorker worker = new RecordingConversionWorker();
        ConversionJobRepository repository = new FindOrStoreOnlyRepository(
                candidate -> new ConversionJobRepository.FindOrStoreResult(candidate, false)
        );

        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new ConversionProperties()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                "hello-viewer".getBytes()
        );

        UUID submitted = service.submit(file);

        assertEquals(0, worker.enqueuedCount());
        assertNotEquals(new UUID(0L, 0L), submitted);
    }

    @Test
    void getJobReturnsRepositoryEntry() {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                new DefaultDocumentValidationService(new ConversionProperties()),
                worker,
                new ConversionProperties()
        );

        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                "contract.docx",
                "application/octet-stream",
                "hash-job",
                1L,
                3
        );
        repository.save(job);

        assertTrue(service.getJob(job.getJobId()).isPresent());
        assertSame(job, service.getJob(job.getJobId()).orElseThrow());
    }

    @Test
    void submitThrowsWhenUploadCannotBeReadForHashing() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new ConversionProperties()
        );

        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("read failed"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.submit(file));

        assertEquals("Unable to read upload for hashing", error.getMessage());
        assertEquals(0, worker.enqueuedCount());
    }

    @Test
    void submitThrowsWhenSha256DigestIsUnavailable() throws Exception {
        ConversionJobRepository repository = new InMemoryConversionJobRepository();
        RecordingConversionWorker worker = new RecordingConversionWorker();
        DocumentConversionService service = new DefaultDocumentConversionService(
                repository,
                file -> {
                },
                worker,
                new ConversionProperties()
        );

        MultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/octet-stream",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))
        );

        Provider[] providers = Security.getProviders();
        for (Provider provider : providers) {
            Security.removeProvider(provider.getName());
        }

        try {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.submit(file));
            assertEquals("SHA-256 digest unavailable", error.getMessage());
        } finally {
            for (int index = 0; index < providers.length; index++) {
                Security.insertProviderAt(providers[index], index + 1);
            }
        }

        assertEquals(0, worker.enqueuedCount());
    }

    private static class RecordingConversionWorker implements ConversionWorker {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void enqueue(UUID jobId) {
            count.incrementAndGet();
        }

        int enqueuedCount() {
            return count.get();
        }
    }

    private static class FindOrStoreOnlyRepository implements ConversionJobRepository {
        private final java.util.function.Function<ConversionJob, ConversionJobRepository.FindOrStoreResult> finder;

        FindOrStoreOnlyRepository(java.util.function.Function<ConversionJob, ConversionJobRepository.FindOrStoreResult> finder) {
            this.finder = finder;
        }

        @Override
        public ConversionJob save(ConversionJob job) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<ConversionJob> findById(UUID jobId) {
            return Optional.empty();
        }

        @Override
        public Optional<ConversionJob> findByContentHash(String contentHash) {
            return Optional.empty();
        }

        @Override
        public ConversionJobRepository.FindOrStoreResult findOrStoreByContentHash(ConversionJob candidate) {
            return finder.apply(candidate);
        }
    }
}
