package com.clearfolio.viewer.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.ConversionJobStatusResponse;
import com.clearfolio.viewer.api.SubmitConversionResponse;
import com.clearfolio.viewer.api.ViewerBootstrapResponse;
import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * HTTP endpoints for submitting conversions and reading conversion results.
 */
@RestController
public class ConversionController {

    private final DocumentConversionService conversionService;

    /**
     * Creates a controller that delegates conversion operations to the service layer.
     *
     * @param conversionService conversion service
     */
    public ConversionController(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Submits a file for asynchronous conversion.
     *
     * @param file uploaded source file
     * @return accepted response containing the job identifier
     */
    @PostMapping(value = "/api/v1/convert/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitConversionResponse> submit(@RequestPart("file") MultipartFile file) {
        UUID jobId = conversionService.submit(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(SubmitConversionResponse.accepted(jobId));
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
