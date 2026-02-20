package com.clearfolio.viewer.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;
import com.clearfolio.viewer.service.DocumentConversionService;

@WebMvcTest(ConversionController.class)
class ConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentConversionService conversionService;

    @Test
    void submitReturnsAcceptedWithJobId() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(conversionService.submit(Mockito.any())).thenReturn(jobId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "hello".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(file)
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.jobId", equalTo(jobId.toString())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.statusUrl", equalTo("/api/v1/convert/jobs/" + jobId)));
    }

    @Test
    void submitReturnsUnsupportedFormatErrorPayload() throws Exception {
        when(conversionService.submit(any())).thenThrow(new UnsupportedDocumentFormatException("hwp"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.hwp",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "hello".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(file)
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("UNSUPPORTED_FORMAT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("UNSUPPORTED_FORMAT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.details.extension", equalTo("hwp")))
                         .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void submitReturnsBadRequestWhenFilePartIsMissing() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void statusReturnsNotFoundWhenJobMissing() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(conversionService.getJob(jobId)).thenReturn(Optional.empty());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/convert/jobs/{jobId}", jobId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("NOT_FOUND")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("NOT_FOUND")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", equalTo("job not found")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void statusReturnsBadRequestForMalformedJobId() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/convert/jobs/{jobId}", "not-a-uuid"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void statusReturnsJobWhenFound() throws Exception {
        UUID jobId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                jobId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        when(conversionService.getJob(jobId)).thenReturn(Optional.of(job));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/convert/jobs/{jobId}", jobId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.jobId", equalTo(jobId.toString())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status", equalTo(ConversionJobStatus.SUBMITTED.name())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fileName", equalTo("report.docx")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.attemptCount", equalTo(0)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.maxAttempts", equalTo(3)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.deadLettered", equalTo(false)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.retryAt", nullValue()));
    }

    @Test
    void statusReturnsDeadLetteredMetadataWhenJobIsTerminalFailed() throws Exception {
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/convert/jobs/{jobId}", jobId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status", equalTo(ConversionJobStatus.FAILED.name())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.deadLettered", equalTo(true)));
    }

    @Test
    void viewerReturnsConflictForSubmittedStatus() throws Exception {
        UUID docId = UUID.randomUUID();
        ConversionJob job = new ConversionJob(
                docId,
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "abc",
                12L
        );
        when(conversionService.getJob(docId)).thenReturn(Optional.of(job));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viewer/{docId}", docId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("retry")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void viewerReturnsConflictForProcessingStatus() throws Exception {
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viewer/{docId}", docId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("retry")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void viewerReturnsConflictForFailedStatus() throws Exception {
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viewer/{docId}", docId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("FAILED")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void viewerReturnsConflictForDeadLetteredStatus() throws Exception {
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viewer/{docId}", docId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("CONFLICT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("DEAD_LETTERED")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void viewerReturnsBootstrapForSucceededStatus() throws Exception {
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viewer/{docId}", docId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.docId", equalTo(docId.toString())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status", equalTo(ConversionJobStatus.SUCCEEDED.name())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fileName", equalTo("report.docx")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.previewResourcePath", equalTo("/artifacts/report.pdf")));
    }

    @Test
    void viewerReturnsNotFoundWhenJobMissing() throws Exception {
        UUID docId = UUID.randomUUID();
        when(conversionService.getJob(docId)).thenReturn(Optional.empty());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/viewer/{docId}", docId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("NOT_FOUND")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("NOT_FOUND")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", equalTo("job not found")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void viewerAliasRoutesReturnConflictForSubmittedStatus() throws Exception {
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
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint, docId))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("CONFLICT")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("retry")));
        }
    }

    @Test
    void viewerAliasRoutesReturnBootstrapWhenReady() throws Exception {
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
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint, docId))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.docId", equalTo(docId.toString())))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status", equalTo(ConversionJobStatus.SUCCEEDED.name())))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.previewResourcePath", equalTo("/artifacts/report.pdf")));
        }
    }
}
