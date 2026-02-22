package com.clearfolio.viewer.service;

import java.util.Optional;
import java.util.UUID;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.repository.ConversionJobRepository;
import com.clearfolio.viewer.config.ConversionProperties;

/**
 * Default implementation that validates uploads, deduplicates by content hash,
 * and enqueues newly created conversion jobs.
 */
@Service
public class DefaultDocumentConversionService implements DocumentConversionService {

    private final ConversionJobRepository repository;
    private final DocumentValidationService validationService;
    private final ConversionWorker conversionWorker;
    private final int maxRetryAttempts;

    /**
     * Creates the conversion service with repository, validation, and worker dependencies.
     *
     * @param repository conversion job repository
     * @param validationService document validation service
     * @param conversionWorker conversion worker
     * @param conversionProperties conversion configuration values
     */
    public DefaultDocumentConversionService(
            ConversionJobRepository repository,
            DocumentValidationService validationService,
            ConversionWorker conversionWorker,
            ConversionProperties conversionProperties) {
        this.repository = repository;
        this.validationService = validationService;
        this.conversionWorker = conversionWorker;
        this.maxRetryAttempts = conversionProperties.getMaxRetryAttempts();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file) {
        return submit(file, PolicyOverrideRequest.none());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID submit(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        PolicyOverrideRequest effectiveOverride = overrideRequest == null
                ? PolicyOverrideRequest.none()
                : overrideRequest;
        validationService.validateOrThrow(file, effectiveOverride);

        String contentHash = contentHash(file);
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                file.getOriginalFilename(),
                file.getContentType(),
                contentHash,
                file.getSize(),
                maxRetryAttempts
        );

        ConversionJobRepository.FindOrStoreResult result = repository.findOrStoreByContentHash(job);
        if (result.created()) {
            conversionWorker.enqueue(result.canonicalJob().getJobId());
        }

        return result.canonicalJob().getJobId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ConversionJob> getJob(UUID jobId) {
        return repository.findById(jobId);
    }

    private String contentHash(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;

            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            byte[] raw = digest.digest();
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read upload for hashing", ex);
        }
    }
}
