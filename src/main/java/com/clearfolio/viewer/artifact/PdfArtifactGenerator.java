package com.clearfolio.viewer.artifact;

import com.clearfolio.viewer.model.ConversionJob;

/**
 * Generates PDF preview artifacts for conversion jobs.
 */
public interface PdfArtifactGenerator {

    /**
     * Generates a complete PDF file for the supplied conversion job.
     *
     * @param job conversion job metadata used to label the PDF
     * @return PDF bytes
     */
    byte[] generatePdf(ConversionJob job);
}
