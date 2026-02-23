package com.clearfolio.viewer.artifact;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores and retrieves preview artifacts produced by conversion jobs.
 *
 * <p>This MVP abstraction intentionally keeps operations in-memory and CPU-only
 * so request paths remain non-blocking (no filesystem/network I/O).
 */
public interface ArtifactStore {

    /**
     * Stores the converted PDF bytes for a document.
     *
     * @param docId document identifier
     * @param pdfBytes complete PDF file bytes
     */
    void putPdf(UUID docId, byte[] pdfBytes);

    /**
     * Reads stored PDF bytes for a document.
     *
     * @param docId document identifier
     * @return PDF bytes when present
     */
    Optional<byte[]> getPdf(UUID docId);
}
