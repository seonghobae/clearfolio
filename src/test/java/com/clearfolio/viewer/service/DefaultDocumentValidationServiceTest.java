package com.clearfolio.viewer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;

class DefaultDocumentValidationServiceTest {

    @Test
    void rejectsHwpAndHwpxByDefault() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.hwp", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("hwp", ex.getExtension());
    }

    @Test
    void allowsSupportedExtensions() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertDoesNotThrow(() -> validationService.validateOrThrow(
                new MockMultipartFile("file", "contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1})
        ));
    }

    @Test
    void rejectsMissingExtension() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("File extension is required.", ex.getMessage());
    }

    @Test
    void rejectsBlankFilenameOrMissingName() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "", "application/octet-stream", new byte[] {1})
                )
        );
    }

    @Test
    void rejectsNullFilename() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", (String) null, "application/octet-stream", new byte[] {1})
                )
        );
    }

    @Test
    void rejectsOversizedPayload() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        conversionProperties.setMaxUploadSizeBytes(2L);
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {1, 2, 3})
                )
        );
    }

    @Test
    void rejectsNullFile() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(null)
        );

        assertEquals("File is required.", ex.getMessage());
    }

    @Test
    void rejectsEmptyFile() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.docx", "application/octet-stream", new byte[0])
                )
        );

        assertEquals("File is required.", ex.getMessage());
    }

    @Test
    void rejectsMultipartWithNullOriginalFilename() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getSize()).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(file)
        );

        assertEquals("File extension is required.", ex.getMessage());
    }

    @Test
    void rejectsFilenameEndingWithDot() {
        ConversionProperties conversionProperties = new ConversionProperties();
        conversionProperties.setBlockedExtensions(Set.of("hwp", "hwpx"));
        DefaultDocumentValidationService validationService = new DefaultDocumentValidationService(conversionProperties);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateOrThrow(
                        new MockMultipartFile("file", "contract.", "application/octet-stream", new byte[] {1})
                )
        );

        assertEquals("File extension is required.", ex.getMessage());
    }
}
