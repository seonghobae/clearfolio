package com.clearfolio.viewer.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.util.unit.DataSize;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.ConversionJobStatusResponse;
import com.clearfolio.viewer.api.SubmitConversionResponse;
import com.clearfolio.viewer.api.ViewerBootstrapResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * HTTP endpoints for submitting conversions and reading conversion results.
 */
@RestController
public class ConversionController {

    private final DocumentConversionService conversionService;
    private final int maxInMemorySizeBytes;

    /**
     * Creates a controller that delegates conversion operations to the service layer.
     *
     * @param conversionService conversion service
     * @param maxInMemorySize maximum in-memory multipart size
     */
    public ConversionController(
            DocumentConversionService conversionService,
            @Value("${spring.codec.max-in-memory-size:262144B}") DataSize maxInMemorySize) {
        this.conversionService = conversionService;
        long bytes = Math.max(1L, maxInMemorySize.toBytes());
        this.maxInMemorySizeBytes = bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    /**
     * Submits a file for asynchronous conversion.
     *
     * @param file uploaded source file
     * @return accepted response containing the job identifier
     */
    @PostMapping(value = "/api/v1/convert/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<SubmitConversionResponse>> submit(@RequestPart("file") FilePart file) {
        return DataBufferUtils.join(file.content(), maxInMemorySizeBytes)
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                .publishOn(Schedulers.boundedElastic())
                .map(buffer -> toMultipartFile(file, buffer))
                .map(conversionService::submit)
                .map(jobId -> ResponseEntity.status(HttpStatus.ACCEPTED).body(SubmitConversionResponse.accepted(jobId)));
    }

    private InMemoryMultipartFile toMultipartFile(FilePart filePart, DataBuffer dataBuffer) {
        byte[] content = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(content);
        DataBufferUtils.release(dataBuffer);

        String contentType = null;
        if (filePart.headers().containsKey(HttpHeaders.CONTENT_TYPE)) {
            contentType = filePart.headers().getContentType() == null
                    ? null
                    : filePart.headers().getContentType().toString();
        }

        return new InMemoryMultipartFile("file", filePart.filename(), contentType, content);
    }

    /**
     * Returns the current status of a conversion job.
     *
     * @param jobId conversion job identifier
     * @return conversion status payload
     */
    @GetMapping("/api/v1/convert/jobs/{jobId}")
    public ConversionJobStatusResponse getStatus(@PathVariable UUID jobId) {
        ConversionJob job = conversionService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        return ConversionJobStatusResponse.from(job);
    }

    /**
     * Returns viewer bootstrap data once conversion output is ready.
     *
     * @param docId document identifier
     * @return viewer bootstrap payload for a converted document
     */
    @GetMapping({"/viewer/{docId}", "/api/v1/viewer/{docId}", "/api/v1/convert/viewer/{docId}"})
    public ViewerBootstrapResponse getViewer(@PathVariable("docId") UUID docId) {
        ConversionJob job = conversionService.getJob(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));

        if (job.getStatus() == ConversionJobStatus.SUCCEEDED) {
            return ViewerBootstrapResponse.from(job);
        }

        if (job.getStatus() == ConversionJobStatus.FAILED) {
            String statusLabel = job.isDeadLettered() ? "DEAD_LETTERED" : "FAILED";
            throw new ResponseStatusException(HttpStatus.CONFLICT, statusLabel + ": " + job.getStatusMessage());
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                job.getStatus() + " not ready yet. retry in a few seconds"
        );
    }
}
