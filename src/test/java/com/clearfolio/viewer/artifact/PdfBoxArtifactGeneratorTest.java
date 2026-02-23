package com.clearfolio.viewer.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

class PdfBoxArtifactGeneratorTest {

    @Test
    void generatePdfReturnsPdfHeaderWhenMetadataMissing() {
        PdfBoxArtifactGenerator generator = new PdfBoxArtifactGenerator();
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                null,
                "application/octet-stream",
                null,
                1L,
                1
        );

        byte[] bytes = generator.generatePdf(job);

        assertNotNull(bytes);
        assertTrue(bytes.length > 4);
        assertEquals("%PDF", new String(bytes, 0, 4));
    }

    @Test
    void generatePdfAcceptsUnicodeInFileNameBySanitizing() {
        PdfBoxArtifactGenerator generator = new PdfBoxArtifactGenerator();
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report-\u2603.docx",
                "application/octet-stream",
                "abc",
                1L,
                1
        );

        byte[] bytes = generator.generatePdf(job);

        assertTrue(bytes.length > 4);
        assertEquals("%PDF", new String(bytes, 0, 4));
    }

    @Test
    void generatePdfTreatsBlankMetadataAsUnknown() {
        PdfBoxArtifactGenerator generator = new PdfBoxArtifactGenerator();
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "   ",
                "application/octet-stream",
                "   ",
                1L,
                1
        );

        byte[] bytes = generator.generatePdf(job);

        assertTrue(bytes.length > 4);
        assertEquals("%PDF", new String(bytes, 0, 4));
    }

    @Test
    void generatePdfSanitizesControlCharacters() {
        PdfBoxArtifactGenerator generator = new PdfBoxArtifactGenerator();
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report\nname.docx",
                "application/octet-stream",
                "hash-\tabc",
                1L,
                1
        );

        byte[] bytes = generator.generatePdf(job);

        assertTrue(bytes.length > 4);
        assertEquals("%PDF", new String(bytes, 0, 4));
    }

    @Test
    void generatePdfThrowsWhenOutputStreamFails() {
        PdfBoxArtifactGenerator.OutputTargetFactory factory = () -> {
            OutputStream failing = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    throw new IOException("write failed");
                }
            };
            return new PdfBoxArtifactGenerator.OutputTarget(failing, () -> new byte[0]);
        };
        PdfBoxArtifactGenerator generator = new PdfBoxArtifactGenerator(factory);
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/octet-stream",
                "abc",
                1L,
                1
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> generator.generatePdf(job));

        assertEquals("failed to generate PDF artifact", error.getMessage());
        assertTrue(error.getCause() instanceof IOException);
        assertEquals("write failed", error.getCause().getMessage());
    }
}
