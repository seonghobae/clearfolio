package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.service.DocumentConversionService;

class ViewerUiControllerTest {

    private DocumentConversionService conversionService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        ViewerUiController controller = new ViewerUiController(conversionService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void viewerReturnsNotFoundHtmlWhenJobMissing() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("clearfolio-doc-id\" content=\"" + docId));
                    assertTrue(body.contains("clearfolio-initial-state\" content=\"NOT_FOUND\""));
                    assertTrue(body.contains("/assets/viewer/viewer.css"));
                    assertTrue(body.contains("/assets/viewer/viewer.js"));
                });
    }

    @Test
    void viewerReturnsLoadingHtmlWhenSubmitted() {
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
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-initial-state\" content=\"LOADING\"")));
    }

    @Test
    void viewerReturnsFailedHtmlWhenJobFailed() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markFailed("boom");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-initial-state\" content=\"FAILED\"")));
    }

    @Test
    void viewerReturnsHtmlWhenJobSucceededWithConvertedResourcePath() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded("/artifacts/" + docId + ".pdf", "done");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("clearfolio-doc-id\" content=\"" + docId)));
    }

    @Test
    void viewerReturnsHtmlWhenJobSucceededButConvertedResourcePathIsBlank() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded("   ", "done");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void viewerReturnsHtmlWhenJobSucceededButConvertedResourcePathIsNull() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/octet-stream",
                "hash",
                10L,
                1
        );
        job.markSucceeded(null, "done");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
