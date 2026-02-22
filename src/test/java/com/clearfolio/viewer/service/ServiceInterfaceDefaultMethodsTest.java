package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.model.ConversionJob;

class ServiceInterfaceDefaultMethodsTest {

    @Test
    void documentConversionServiceDefaultMethodDelegatesToLegacySubmit() {
        UUID expected = UUID.randomUUID();
        DocumentConversionService service = new DocumentConversionService() {
            @Override
            public UUID submit(MultipartFile file) {
                return expected;
            }

            @Override
            public Optional<ConversionJob> getJob(UUID jobId) {
                return Optional.empty();
            }
        };

        UUID actual = service.submit(
                new MockMultipartFile("file", "report.docx", "application/octet-stream", new byte[] {1}),
                PolicyOverrideRequest.of("true", "token-123", "approver-1")
        );

        assertEquals(expected, actual);
    }

    @Test
    void documentValidationServiceDefaultMethodDelegatesToLegacyValidation() {
        AtomicReference<MultipartFile> capturedFile = new AtomicReference<>();
        DocumentValidationService service = capturedFile::set;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/octet-stream",
                new byte[] {1}
        );

        service.validateOrThrow(file, PolicyOverrideRequest.of("true", "token-123", "approver-1"));

        assertEquals(file, capturedFile.get());
    }
}
