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

    @Test
    void usesGenericMessageWhenExtensionIsNull() {
        UnsupportedDocumentFormatException error = new UnsupportedDocumentFormatException(null);

        assertEquals(null, error.getExtension());
        assertEquals("Unsupported format. hwp/hwpx documents are blocked by default.", error.getMessage());
    }

    @Test
    void usesGenericMessageWhenExtensionIsBlank() {
        UnsupportedDocumentFormatException error = new UnsupportedDocumentFormatException(" ");

        assertEquals(" ", error.getExtension());
        assertEquals("Unsupported format. hwp/hwpx documents are blocked by default.", error.getMessage());
    }
}
