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
        if (extension == null || extension.isBlank()) {
            return "Unsupported format. hwp/hwpx documents are blocked by default.";
        }
        return "Unsupported format: " + extension + ". hwp/hwpx documents are blocked by default.";
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
