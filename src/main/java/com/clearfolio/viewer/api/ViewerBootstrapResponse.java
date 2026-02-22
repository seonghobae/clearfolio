package com.clearfolio.viewer.api;

import java.time.Instant;
import java.util.Locale;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * API payload that initializes the viewer for a converted document.
 */
public record ViewerBootstrapResponse(
        String docId,
        String status,
        String fileName,
        String viewerMode,
        String previewResourcePath,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String sourceExtension,
        String rendererAdapter
) {

    private static final String PDF_JS = "PDF_JS";

    /**
     * Creates a viewer bootstrap response from a conversion job.
     *
     * @param job completed conversion job
     * @return mapped viewer bootstrap payload
     */
    public static ViewerBootstrapResponse from(ConversionJob job) {
        String sourceExtension = sourceExtensionOf(job.getOriginalFileName());
        String rendererAdapter = rendererAdapterFor(sourceExtension);
        return new ViewerBootstrapResponse(
                job.getJobId().toString(),
                job.getStatus().name(),
                job.getOriginalFileName(),
                PDF_JS,
                job.getConvertedResourcePath(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                sourceExtension,
                rendererAdapter
        );
    }

    private static String sourceExtensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private static String rendererAdapterFor(String sourceExtension) {
        return switch (sourceExtension) {
            case "pdf" -> PDF_JS;
            case "doc", "docx" -> "DOCX_PREVIEW";
            case "xls", "xlsx", "csv", "tsv" -> "SHEET_ADAPTER";
            case "ppt", "pptx" -> "SLIDE_ADAPTER";
            case "md", "txt" -> "TEXT_ADAPTER";
            default -> PDF_JS;
        };
    }
}
