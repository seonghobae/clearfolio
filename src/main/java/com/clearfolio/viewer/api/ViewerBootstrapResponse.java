package com.clearfolio.viewer.api;

import java.time.Instant;

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
        Instant completedAt
) {

    /**
     * Creates a viewer bootstrap response from a conversion job.
     *
     * @param job completed conversion job
     * @return mapped viewer bootstrap payload
     */
    public static ViewerBootstrapResponse from(ConversionJob job) {
        return new ViewerBootstrapResponse(
                job.getJobId().toString(),
                job.getStatus().name(),
                job.getOriginalFileName(),
                "PDF_JS",
                job.getConvertedResourcePath(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
