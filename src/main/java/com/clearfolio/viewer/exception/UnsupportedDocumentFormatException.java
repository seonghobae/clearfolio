package com.clearfolio.viewer.exception;

/**
 * Exception raised when an uploaded document extension is blocked.
 */
public class UnsupportedDocumentFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String extension;

    /**
     * Creates an exception for a blocked extension.
     *
     * @param extension blocked extension value
     */
    public UnsupportedDocumentFormatException(String extension) {
        super("Unsupported format: " + extension + ". hwp/hwpx documents are blocked by default.");
        this.extension = extension;
    }

    /**
     * Returns the blocked extension that triggered this exception.
     *
     * @return blocked extension
     */
    public String getExtension() {
        return extension;
    }
}
