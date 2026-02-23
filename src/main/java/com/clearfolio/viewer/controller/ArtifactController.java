package com.clearfolio.viewer.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;

import reactor.core.publisher.Mono;

/**
 * Serves converted preview artifacts.
 */
@RestController
public class ArtifactController {

    private static final String RANGE_UNIT_BYTES = "bytes";

    private final DocumentConversionService conversionService;
    private final ArtifactStore artifactStore;

    /**
     * Creates a controller that serves stored conversion artifacts.
     *
     * @param conversionService conversion service for status gating
     * @param artifactStore artifact store for PDF bytes
     */
    public ArtifactController(DocumentConversionService conversionService, ArtifactStore artifactStore) {
        this.conversionService = conversionService;
        this.artifactStore = artifactStore;
    }

    /**
     * Serves the converted PDF artifact for a document when conversion succeeded.
     *
     * <p>Only a single HTTP range is supported.
     *
     * @param docId document identifier
     * @param rangeHeader optional {@code Range} header
     * @return PDF bytes when available
     */
    @GetMapping(value = "/artifacts/{docId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> getPdf(
            @PathVariable UUID docId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        Optional<ConversionJob> job = conversionService.getJob(docId);
        if (job.isEmpty() || job.get().getStatus() != ConversionJobStatus.SUCCEEDED) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        Optional<byte[]> stored = artifactStore.getPdf(docId);
        if (stored.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        byte[] pdfBytes = stored.get();
        int totalLength = pdfBytes.length;

        Optional<ResolvedRange> range = resolveSingleRange(rangeHeader, totalLength);
        if (range.isPresent() && range.get().unsatisfiable()) {
            return Mono.just(unsatisfiable(totalLength));
        }
        if (range.isPresent() && range.get().invalid()) {
            return Mono.just(unsatisfiable(totalLength));
        }

        if (range.isEmpty()) {
            return Mono.just(full(pdfBytes));
        }

        ResolvedRange resolved = range.get();
        int start = resolved.startInclusive();
        int end = resolved.endInclusive();
        int length = end - start + 1;
        byte[] slice = java.util.Arrays.copyOfRange(pdfBytes, start, end + 1);
        return Mono.just(partial(slice, start, end, totalLength, length));
    }

    private static ResponseEntity<byte[]> full(byte[] pdfBytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, RANGE_UNIT_BYTES)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }

    private static ResponseEntity<byte[]> partial(
            byte[] body,
            int start,
            int end,
            int total,
            int length) {
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, RANGE_UNIT_BYTES)
                .header(HttpHeaders.CONTENT_RANGE, contentRange(start, end, total))
                .contentLength(length)
                .body(body);
    }

    private static ResponseEntity<byte[]> unsatisfiable(int totalLength) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, RANGE_UNIT_BYTES)
                .header(HttpHeaders.CONTENT_RANGE, RANGE_UNIT_BYTES + " */" + totalLength)
                .build();
    }

    private static String contentRange(int start, int end, int total) {
        return RANGE_UNIT_BYTES + " " + start + "-" + end + "/" + total;
    }

    private Optional<ResolvedRange> resolveSingleRange(String rangeHeader, int totalLength) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rangeHeader.strip();
        if (!trimmed.startsWith(RANGE_UNIT_BYTES + "=")) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        String spec = trimmed.substring((RANGE_UNIT_BYTES + "=").length()).strip();
        if (spec.isEmpty()) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        if (spec.contains(",")) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        int dash = spec.indexOf('-');
        if (dash < 0) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        String first = spec.substring(0, dash).strip();
        String second = spec.substring(dash + 1).strip();

        if (first.isEmpty()) {
            return resolveSuffix(second, totalLength);
        }

        return resolveStartEnd(first, second, totalLength);
    }

    private Optional<ResolvedRange> resolveStartEnd(String first, String second, int totalLength) {
        long startLong;
        try {
            startLong = Long.parseLong(first);
        } catch (NumberFormatException ex) {
            return Optional.of(ResolvedRange.invalidRange());
        }
        if (startLong >= totalLength) {
            return Optional.of(ResolvedRange.unsatisfiableRange());
        }

        int start = (int) startLong;

        if (second.isEmpty()) {
            return Optional.of(ResolvedRange.ok(start, totalLength - 1));
        }

        long endLong;
        try {
            endLong = Long.parseLong(second);
        } catch (NumberFormatException ex) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        if (endLong < startLong) {
            return Optional.of(ResolvedRange.unsatisfiableRange());
        }

        long boundedEnd = Math.min(endLong, totalLength - 1L);
        return Optional.of(ResolvedRange.ok(start, (int) boundedEnd));
    }

    private Optional<ResolvedRange> resolveSuffix(String suffix, int totalLength) {
        if (suffix.isEmpty()) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        long suffixLong;
        try {
            suffixLong = Long.parseLong(suffix);
        } catch (NumberFormatException ex) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        if (suffixLong <= 0L) {
            return Optional.of(ResolvedRange.invalidRange());
        }

        if (suffixLong >= totalLength) {
            return Optional.of(ResolvedRange.ok(0, totalLength - 1));
        }

        long startLong = totalLength - suffixLong;
        return Optional.of(ResolvedRange.ok((int) startLong, totalLength - 1));
    }

    private record ResolvedRange(
            int startInclusive,
            int endInclusive,
            boolean invalid,
            boolean unsatisfiable
    ) {
        static ResolvedRange ok(int startInclusive, int endInclusive) {
            return new ResolvedRange(startInclusive, endInclusive, false, false);
        }

        static ResolvedRange invalidRange() {
            return new ResolvedRange(0, 0, true, false);
        }

        static ResolvedRange unsatisfiableRange() {
            return new ResolvedRange(0, 0, false, true);
        }
    }
}
