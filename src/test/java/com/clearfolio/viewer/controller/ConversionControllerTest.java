package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.BodyInserters;

import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;
import com.clearfolio.viewer.service.PolicyOverrideRequest;
import com.clearfolio.viewer.service.RetryDeadLetterResult;

class ConversionControllerTest {

    private WebTestClient webTestClient;

    private DocumentConversionService conversionService;

    private ConversionController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        controller = new ConversionController(conversionService, DataSize.ofBytes(262_144L));
        webTestClient = WebTestClient.bindToController(
                controller
        ).controllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void constructorCapsMaxInMemorySizeAtIntegerMaxValue() throws Exception {
        ConversionController controller = new ConversionController(
                conversionService,
                DataSize.ofBytes((long) Integer.MAX_VALUE + 1)
        );
        Field field = ConversionController.class.getDeclaredField("maxInMemorySizeBytes");
        field.setAccessible(true);

        assertEquals(Integer.MAX_VALUE, field.getInt(controller));
    }

    @Test
    void toMultipartFileHandlesMissingContentTypeHeader() throws Exception {
        FilePart filePart = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        when(filePart.headers()).thenReturn(headers);
        when(filePart.filename()).thenReturn("report.docx");

        DataBuffer dataBuffer = DefaultDataBufferFactory.sharedInstance.wrap("abc".getBytes());
        Method method = ConversionController.class.getDeclaredMethod(
                "toMultipartFile",
                FilePart.class,
                DataBuffer.class
        );
        method.setAccessible(true);

        InMemoryMultipartFile file = (InMemoryMultipartFile) method.invoke(controller, filePart, dataBuffer);

        assertNull(file.getContentType());
        assertEquals("report.docx", file.getOriginalFilename());
        assertEquals(3L, file.getSize());
    }

    @Test
    void toMultipartFileHandlesNullContentTypeValue() throws Exception {
        FilePart filePart = mock(FilePart.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.containsKey(HttpHeaders.CONTENT_TYPE)).thenReturn(true);
        when(headers.getContentType()).thenReturn(null);
        when(filePart.headers()).thenReturn(headers);
        when(filePart.filename()).thenReturn("report.docx");

        DataBuffer dataBuffer = DefaultDataBufferFactory.sharedInstance.wrap("abc".getBytes());
        Method method = ConversionController.class.getDeclaredMethod(
                "toMultipartFile",
                FilePart.class,
                DataBuffer.class
        );
        method.setAccessible(true);

        InMemoryMultipartFile file = (InMemoryMultipartFile) method.invoke(controller, filePart, dataBuffer);

        assertNull(file.getContentType());
    }

    @Test
    void toMultipartFileCopiesContentTypeHeaderValue() throws Exception {
        FilePart filePart = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        when(filePart.headers()).thenReturn(headers);
        when(filePart.filename()).thenReturn("report.pdf");

        DataBuffer dataBuffer = DefaultDataBufferFactory.sharedInstance.wrap("abc".getBytes());
        Method method = ConversionController.class.getDeclaredMethod(
                "toMultipartFile",
                FilePart.class,
                DataBuffer.class
        );
        method.setAccessible(true);

        InMemoryMultipartFile file = (InMemoryMultipartFile) method.invoke(controller, filePart, dataBuffer);

        assertEquals("application/pdf", file.getContentType());
        assertEquals("report.pdf", file.getOriginalFilename());
    }

    @Test
    void submitReturnsAcceptedWithJobId() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.submit(any(), any())).thenReturn(jobId);

        submit("report.docx", "hello".getBytes())
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId.toString())
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.statusUrl").isEqualTo("/api/v1/convert/jobs/" + jobId);
    }

    @Test
    void submitReturnsUnsupportedFormatErrorPayload() {
        when(conversionService.submit(any(), any())).thenThrow(new UnsupportedDocumentFormatException("hwp"));

        submit("contract.hwp", "hello".getBytes())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNSUPPORTED_FORMAT")
                .jsonPath("$.code").isEqualTo("UNSUPPORTED_FORMAT")
                .jsonPath("$.details.extension").isEqualTo("hwp")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void submitForwardsPolicyOverrideHeadersToService() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.submit(any(), any())).thenReturn(jobId);

        submit("contract.hwp", "hello".getBytes(), "true", "token-123", "approver-1")
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId.toString());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<PolicyOverrideRequest> overrideCaptor =
                org.mockito.ArgumentCaptor.forClass(PolicyOverrideRequest.class);
        verify(conversionService).submit(any(), overrideCaptor.capture());
        PolicyOverrideRequest overrideRequest = overrideCaptor.getValue();
        assertEquals("true", overrideRequest.policyOverride());
        assertEquals("token-123", overrideRequest.approvalToken());
        assertEquals("approver-1", overrideRequest.approverId());
    }

    @Test
    void submitReturnsBadRequestWhenFilePartIsMissing() {
        webTestClient.post()
                .uri("/api/v1/convert/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(new LinkedMultiValueMap<>()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void statusReturnsNotFoundWhenJobMissing() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}", jobId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("NOT_FOUND")
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("job not found")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void statusReturnsBadRequestForMalformedJobId() {
        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}", "not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void statusReturnsJobWhenFound() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}", jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId.toString())
                .jsonPath("$.status").isEqualTo(ConversionJobStatus.SUBMITTED.name())
                .jsonPath("$.fileName").isEqualTo("report.docx")
                .jsonPath("$.attemptCount").isEqualTo(0)
                .jsonPath("$.maxAttempts").isEqualTo(3)
                .jsonPath("$.deadLettered").isEqualTo(false)
                .jsonPath("$.retryAt").isEmpty();
    }

    @Test
    void statusReturnsDeadLetteredMetadataWhenJobIsTerminalFailed() {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        job.markDeadLettered("retries exhausted");
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/convert/jobs/{jobId}", jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(ConversionJobStatus.FAILED.name())
                .jsonPath("$.deadLettered").isEqualTo(true);
    }

    @Test
    void retryReturnsAcceptedWhenDeadLetteredJobIsEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "operator-7")).thenReturn(RetryDeadLetterResult.ACCEPTED);

        webTestClient.post()
                .uri("/api/v1/convert/jobs/{jobId}/retry", jobId)
                .header(ConversionController.OPERATOR_ID_HEADER, "operator-7")
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId.toString())
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.statusUrl").isEqualTo("/api/v1/convert/jobs/" + jobId);

        verify(conversionService).retryDeadLettered(jobId, "operator-7");
    }

    @Test
    void retryReturnsBadRequestWhenOperatorHeaderMissing() {
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/convert/jobs/{jobId}/retry", jobId)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo(ConversionController.OPERATOR_ID_HEADER + " header is required.")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void retryReturnsBadRequestWhenOperatorHeaderIsBlank() {
        UUID jobId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/convert/jobs/{jobId}/retry", jobId)
                .header(ConversionController.OPERATOR_ID_HEADER, "   ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo(ConversionController.OPERATOR_ID_HEADER + " header is required.")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void retryReturnsNotFoundWhenJobMissing() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "operator-7")).thenReturn(RetryDeadLetterResult.NOT_FOUND);

        webTestClient.post()
                .uri("/api/v1/convert/jobs/{jobId}/retry", jobId)
                .header(ConversionController.OPERATOR_ID_HEADER, "operator-7")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("NOT_FOUND")
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("job not found")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void retryReturnsConflictWhenJobIsNotEligible() {
        UUID jobId = UUID.randomUUID();
        when(conversionService.retryDeadLettered(jobId, "operator-7")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);

        webTestClient.post()
                .uri("/api/v1/convert/jobs/{jobId}/retry", jobId)
                .header(ConversionController.OPERATOR_ID_HEADER, "operator-7")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONFLICT")
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").isEqualTo("only dead-lettered failed jobs can be retried")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void viewerReturnsConflictForSubmittedStatus() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONFLICT")
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").value(value -> assertContains((String) value, "retry"))
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void viewerReturnsConflictForProcessingStatus() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        job.markProcessing("conversion started");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONFLICT")
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").value(value -> assertContains((String) value, "retry"))
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void viewerReturnsConflictForFailedStatus() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        job.markFailed("conversion failed");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONFLICT")
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").value(value -> assertContains((String) value, "FAILED"))
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void viewerReturnsConflictForDeadLetteredStatus() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        job.markDeadLettered("retries exhausted");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("CONFLICT")
                .jsonPath("$.code").isEqualTo("CONFLICT")
                .jsonPath("$.message").value(value -> assertContains((String) value, "DEAD_LETTERED"))
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void viewerReturnsBootstrapForSucceededStatus() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        job.markSucceeded("/artifacts/report.pdf", "conversion completed");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.docId").isEqualTo(docId.toString())
                .jsonPath("$.status").isEqualTo(ConversionJobStatus.SUCCEEDED.name())
                .jsonPath("$.fileName").isEqualTo("report.docx")
                .jsonPath("$.previewResourcePath").isEqualTo("/artifacts/report.pdf")
                .jsonPath("$.sourceExtension").isEqualTo("docx")
                .jsonPath("$.rendererAdapter").isEqualTo("DOCX_PREVIEW");
    }

    @Test
    void viewerReturnsNotFoundWhenJobMissing() {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        webTestClient.get()
                .uri("/api/v1/viewer/{docId}", docId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("NOT_FOUND")
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("job not found")
                .jsonPath("$.traceId").value(ConversionControllerTest::assertNonBlankTraceId);
    }

    @Test
    void viewerAliasRoutesReturnConflictForSubmittedStatus() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        String[] aliasEndpoints = {"/api/v1/viewer/{docId}", "/api/v1/convert/viewer/{docId}"};
        for (String endpoint : aliasEndpoints) {
            webTestClient.get()
                    .uri(endpoint, docId)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("CONFLICT")
                    .jsonPath("$.message").value(value -> assertContains((String) value, "retry"));
        }
    }

    @Test
    void viewerAliasRoutesReturnBootstrapWhenReady() {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        job.markSucceeded("/artifacts/report.pdf", "conversion completed");
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        String[] aliasEndpoints = {"/api/v1/viewer/{docId}", "/api/v1/convert/viewer/{docId}"};
        for (String endpoint : aliasEndpoints) {
            webTestClient.get()
                    .uri(endpoint, docId)
                    .exchange()
                    .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.docId").isEqualTo(docId.toString())
                .jsonPath("$.status").isEqualTo(ConversionJobStatus.SUCCEEDED.name())
                .jsonPath("$.previewResourcePath").isEqualTo("/artifacts/report.pdf")
                .jsonPath("$.sourceExtension").isEqualTo("docx")
                .jsonPath("$.rendererAdapter").isEqualTo("DOCX_PREVIEW");
        }
    }

    private WebTestClient.ResponseSpec submit(String filename, byte[] content) {
        return submit(filename, content, null, null, null);
    }

    private WebTestClient.ResponseSpec submit(
            String filename,
            byte[] content,
            String policyOverride,
            String approvalToken,
            String approverId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", content)
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        WebTestClient.RequestBodySpec request = webTestClient.post()
                .uri("/api/v1/convert/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA);
        if (policyOverride != null) {
            request.header(PolicyOverrideRequest.POLICY_OVERRIDE_HEADER, policyOverride);
        }
        if (approvalToken != null) {
            request.header(PolicyOverrideRequest.APPROVAL_TOKEN_HEADER, approvalToken);
        }
        if (approverId != null) {
            request.header(PolicyOverrideRequest.APPROVER_ID_HEADER, approverId);
        }

        return request
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected));
    }

    private static void assertNonBlankTraceId(Object value) {
        String traceId = (String) value;
        assertFalse(traceId.isBlank());
    }
}
