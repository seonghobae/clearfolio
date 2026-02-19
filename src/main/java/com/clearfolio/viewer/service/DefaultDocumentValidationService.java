package com.clearfolio.viewer.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clearfolio.viewer.config.ConversionProperties;
import com.clearfolio.viewer.exception.UnsupportedDocumentFormatException;

/**
 * Default document validator that enforces extension and size constraints.
 */
@Service
public class DefaultDocumentValidationService implements DocumentValidationService {

    private final Set<String> blockedExtensions;
    private final long maxUploadSizeBytes;

    /**
     * Creates the validation service from conversion configuration values.
     *
     * @param conversionProperties conversion configuration values
     */
    public DefaultDocumentValidationService(ConversionProperties conversionProperties) {
        this.blockedExtensions = conversionProperties.getBlockedExtensions();
        this.maxUploadSizeBytes = conversionProperties.getMaxUploadSizeBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateOrThrow(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }

        String fileName = file.getOriginalFilename();
        String extension = extensionOf(fileName);
        if (extension.isEmpty()) {
            throw new IllegalArgumentException("File extension is required.");
        }

        if (blockedExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedDocumentFormatException(extension);
        }

        if (file.getSize() > maxUploadSizeBytes) {
            throw new IllegalArgumentException("File is too large.");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
