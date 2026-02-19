package com.clearfolio.viewer.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
                "conversion.max-upload-size-bytes=1024",
                "spring.servlet.multipart.max-file-size=1024",
                "spring.servlet.multipart.max-request-size=1024"
        }
)
class ConversionControllerMultipartLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void submitReturnsBadRequestWhenUploadExceedsConfiguredMultipartLimit() throws Exception {
        byte[] payload = new byte[2048];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "report.docx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                payload
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(largeFile)
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void submitReturnsBadRequestWhenFilenameIsMissingExtension() throws Exception {
        MockMultipartFile missingExtensionFile = new MockMultipartFile(
                "file",
                "report",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "hello".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(missingExtensionFile)
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("File extension is required")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void submitReturnsUnsupportedFormatForBlockedExtensionWithServiceValidation() throws Exception {
        MockMultipartFile blockedFile = new MockMultipartFile(
                "file",
                "contract.hwp",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "hello".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(blockedFile)
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
    void submitReturnsBadRequestWhenServiceUploadLimitIsExceeded() throws Exception {
        byte[] payload = new byte[1025];
        MockMultipartFile oversizedButParserAllowed = new MockMultipartFile(
                "file",
                "report.docx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                payload
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(oversizedButParserAllowed)
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code", equalTo("BAD_REQUEST")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message", containsString("File is too large")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.traceId", org.hamcrest.Matchers.not(emptyOrNullString())));
    }

    @Test
    void submitAcceptsDocumentAtServiceUploadLimitBoundary() throws Exception {
        byte[] payload = new byte[1024];
        MockMultipartFile onLimitFile = new MockMultipartFile(
                "file",
                "report.docx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                payload
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/convert/jobs")
                        .file(onLimitFile)
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status", equalTo("ACCEPTED")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.jobId", containsString("-")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.statusUrl", containsString("/api/v1/convert/jobs/")));
    }
}
