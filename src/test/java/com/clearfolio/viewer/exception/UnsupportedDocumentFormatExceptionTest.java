package com.clearfolio.viewer.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UnsupportedDocumentFormatExceptionTest {

    @Test
    void exposesExtensionAndMessage() {
        UnsupportedDocumentFormatException error = new UnsupportedDocumentFormatException("hwpx");

        assertEquals("hwpx", error.getExtension());
        assertEquals("Unsupported format: hwpx. hwp/hwpx documents are blocked by default.", error.getMessage());
    }
}
