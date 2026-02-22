package com.clearfolio.viewer.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(
        properties = {
                "conversion.max-upload-size-bytes=1024",
                "spring.codec.max-in-memory-size=2048"
        }
)
class ConversionControllerMultipartLimitTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void submitReturnsBadRequestWhenUploadExceedsReactiveCodecLimit() {
        byte[] payload = new byte[2049];

        submit("report.docx", payload)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo("File is too large.")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitReturnsBadRequestWhenFilenameIsMissingExtension() {
        submit("report", "hello".getBytes())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> assertContains((String) value, "File extension is required"))
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitReturnsUnsupportedFormatForBlockedExtensionWithServiceValidation() {
        submit("contract.hwp", "hello".getBytes())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNSUPPORTED_FORMAT")
                .jsonPath("$.code").isEqualTo("UNSUPPORTED_FORMAT")
                .jsonPath("$.details.extension").isEqualTo("hwp")
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitReturnsBadRequestWhenServiceUploadLimitIsExceeded() {
        byte[] payload = new byte[1025];

        submit("report.docx", payload)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST")
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").value(value -> assertContains((String) value, "File is too large"))
                .jsonPath("$.traceId").value(ConversionControllerMultipartLimitTest::assertNonBlankTraceId);
    }

    @Test
    void submitAcceptsDocumentAtServiceUploadLimitBoundary() {
        byte[] payload = new byte[1024];

        submit("report.docx", payload)
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.jobId").value(value -> assertContains((String) value, "-"))
                .jsonPath("$.statusUrl").value(value -> assertContains((String) value, "/api/v1/convert/jobs/"));
    }

    private WebTestClient.ResponseSpec submit(String filename, byte[] content) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", content)
                .filename(filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return webTestClient.post()
                .uri("/api/v1/convert/jobs")
                .contentType(MediaType.MULTIPART_FORM_DATA)
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
