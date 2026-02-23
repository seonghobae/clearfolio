package com.clearfolio.viewer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.artifact.ArtifactStore;
import com.clearfolio.viewer.artifact.InMemoryArtifactStore;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

class ArtifactControllerTest {

    private DocumentConversionService conversionService;
    private ArtifactStore artifactStore;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        artifactStore = new InMemoryArtifactStore();
        ArtifactController controller = new ArtifactController(conversionService, artifactStore);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void returnsNotFoundWhenJobMissing() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundWhenJobIsNotSucceeded() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsNotFoundWhenArtifactIsMissing() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returnsFullPdfWhenNoRangeHeader() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PDF)
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returnsFullPdfWhenRangeHeaderIsBlank() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "   ")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returnsPartialPdfForExplicitRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=0-3")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-3/10")
                .expectBody(byte[].class).isEqualTo(new byte[] {0, 1, 2, 3});
    }

    @Test
    void returnsPartialPdfForOpenEndedRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=7-")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 7-9/10")
                .expectBody(byte[].class).isEqualTo(new byte[] {7, 8, 9});
    }

    @Test
    void returnsPartialPdfForSuffixRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=-3")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 7-9/10")
                .expectBody(byte[].class).isEqualTo(new byte[] {7, 8, 9});
    }

    @Test
    void returns416ForUnsatisfiableRange() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=100-200")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */10");
    }

    @Test
    void returns416ForInvalidMultipleRanges() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=0-1,2-3")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */10");
    }

    @Test
    void returns416ForInvalidRangeUnit() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "items=0-1")
                .exchange()
                .expectStatus().isEqualTo(416)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes */10");
    }

    @Test
    void returns416ForEmptyRangeSpec() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeHasNoDash() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=123")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeStartIsNotNumeric() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=a-3")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeEndIsNotNumeric() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=0-b")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenRangeEndIsBeforeStart() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=5-2")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void boundsRangeEndToPdfLength() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=0-999")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-9/10")
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    @Test
    void returns416WhenSuffixRangeIsMissingLength() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=-")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenSuffixRangeIsNotNumeric() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=-abc")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returns416WhenSuffixRangeIsZero() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        artifactStore.putPdf(docId, sampleBytes());

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=-0")
                .exchange()
                .expectStatus().isEqualTo(416);
    }

    @Test
    void returnsFullBodyForSuffixRangeLongerThanPdf() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = succeededJob(docId);
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));
        byte[] pdf = sampleBytes();
        artifactStore.putPdf(docId, pdf);

        webTestClient.get()
                .uri("/artifacts/{docId}.pdf", docId)
                .header(HttpHeaders.RANGE, "bytes=-999")
                .exchange()
                .expectStatus().isEqualTo(206)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-9/10")
                .expectBody(byte[].class).isEqualTo(pdf);
    }

    private static ConversionJob succeededJob(UUID docId) {
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded("/artifacts/" + docId + ".pdf", "done");
        return job;
    }

    private static byte[] sampleBytes() {
        return new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    }
}
