package com.clearfolio.viewer.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploaded documents before conversion processing.
 */
public interface DocumentValidationService {
    /**
     * Validates the uploaded file or throws an exception when invalid.
     *
     * @param file uploaded file
     */
    void validateOrThrow(MultipartFile file);

    /**
     * Validates the uploaded file with optional policy-override metadata.
     *
     * @param file uploaded file
     * @param overrideRequest policy-override request headers
     */
    default void validateOrThrow(MultipartFile file, PolicyOverrideRequest overrideRequest) {
        validateOrThrow(file);
    }
}
