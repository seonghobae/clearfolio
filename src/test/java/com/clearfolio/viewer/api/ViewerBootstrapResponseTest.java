package com.clearfolio.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clearfolio.viewer.model.ConversionJob;

class ViewerBootstrapResponseTest {

    @Test
    void mapsRendererAdapterBySourceExtension() {
        Map<String, String> expectedMappings = Map.ofEntries(
                Map.entry("report.pdf", "PDF_JS"),
                Map.entry("report.doc", "DOCX_PREVIEW"),
                Map.entry("report.docx", "DOCX_PREVIEW"),
                Map.entry("report.xls", "SHEET_ADAPTER"),
                Map.entry("report.xlsx", "SHEET_ADAPTER"),
                Map.entry("report.csv", "SHEET_ADAPTER"),
                Map.entry("report.tsv", "SHEET_ADAPTER"),
                Map.entry("report.ppt", "SLIDE_ADAPTER"),
                Map.entry("report.pptx", "SLIDE_ADAPTER"),
                Map.entry("report.md", "TEXT_ADAPTER"),
                Map.entry("report.txt", "TEXT_ADAPTER"),
                Map.entry("report.bin", "PDF_JS")
        );

        for (Map.Entry<String, String> entry : expectedMappings.entrySet()) {
            ConversionJob job = succeededJob(entry.getKey());

            ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

            assertEquals(entry.getValue(), response.rendererAdapter());
            assertEquals("PDF_JS", response.viewerMode());
        }
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameHasNoExtension() {
        ConversionJob job = succeededJob("report");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameEndsWithDot() {
        ConversionJob job = succeededJob("report.");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameIsNull() {
        ConversionJob job = succeededJob(null);

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterWhenFilenameIsBlank() {
        ConversionJob job = succeededJob("   ");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void defaultsSourceExtensionAndAdapterForLeadingDotFileName() {
        ConversionJob job = succeededJob(".gitignore");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("", response.sourceExtension());
        assertEquals("PDF_JS", response.rendererAdapter());
    }

    @Test
    void trimsFilenameBeforeExtractingExtension() {
        ConversionJob job = succeededJob("  report.docx  ");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("docx", response.sourceExtension());
        assertEquals("DOCX_PREVIEW", response.rendererAdapter());
    }

    @Test
    void normalizesSourceExtensionToLowerCase() {
        ConversionJob job = succeededJob("REPORT.DOCX");

        ViewerBootstrapResponse response = ViewerBootstrapResponse.from(job);

        assertEquals("docx", response.sourceExtension());
        assertEquals("DOCX_PREVIEW", response.rendererAdapter());
    }

    private ConversionJob succeededJob(String fileName) {
        ConversionJob job = new ConversionJob(
                UUID.randomUUID(),
                fileName,
                "application/octet-stream",
                "content-hash",
                12L
        );
        job.markSucceeded("/artifacts/result.pdf", "done");
        return job;
    }
}
