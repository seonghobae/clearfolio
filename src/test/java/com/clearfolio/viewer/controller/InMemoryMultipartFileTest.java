package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InMemoryMultipartFileTest {

    @Test
    void storesMetadataAndContentSafely() throws IOException {
        byte[] raw = "hello".getBytes();
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "report.docx",
                "application/octet-stream",
                raw
        );
        raw[0] = 'x';

        assertEquals("file", file.getName());
        assertEquals("report.docx", file.getOriginalFilename());
        assertEquals("application/octet-stream", file.getContentType());
        assertFalse(file.isEmpty());
        assertEquals(5L, file.getSize());
        assertEquals("hello", new String(file.getBytes()));
        assertEquals("hello", new String(file.getInputStream().readAllBytes()));
    }

    @Test
    void getBytesReturnsDefensiveCopy() {
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "report.docx", null, new byte[] {1, 2, 3});

        byte[] copied = file.getBytes();
        copied[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, file.getBytes());
    }

    @Test
    void transferToWritesContent() throws IOException {
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "report.docx", null, "payload".getBytes());
        Path target = Files.createTempFile("in-memory-multipart", ".bin");

        try {
            file.transferTo(target.toFile());
            assertEquals("payload", Files.readString(target));
        } finally {
            Files.deleteIfExists(target);
        }
    }

    @Test
    void transferToPathWritesContent() throws IOException {
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "report.docx", null, "payload".getBytes());
        Path target = Files.createTempFile("in-memory-multipart-path", ".bin");

        try {
            file.transferTo(target);
            assertEquals("payload", Files.readString(target));
        } finally {
            Files.deleteIfExists(target);
        }
    }

    @Test
    void supportsNullContentAsEmptyPayload() {
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "report.docx", null, null);

        assertTrue(file.isEmpty());
        assertEquals(0L, file.getSize());
        assertArrayEquals(new byte[0], file.getBytes());
    }

    @Test
    void rejectsNullName() {
        assertThrows(
                NullPointerException.class,
                () -> new InMemoryMultipartFile(null, "report.docx", null, new byte[] {1})
        );
    }
}
