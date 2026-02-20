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
        super(buildMessage(extension));
        this.extension = extension;
    }

    private static String buildMessage(String extension) {
        String suffix = "This document type is blocked by policy.";
        if (extension == null || extension.isBlank()) {
            return suffix;
        }
        return "Unsupported format: " + extension + ". " + suffix;
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
