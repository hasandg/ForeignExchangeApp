package com.hasandag.exchange.conversion.service;

import com.hasandag.exchange.common.exception.BatchJobException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileValidationService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final String[] ALLOWED_CONTENT_TYPES = {"text/csv", "text/plain", "application/vnd.ms-excel"};

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BatchJobException.emptyFile();
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BatchJobException(
                "FILE_TOO_LARGE",
                "File size exceeds maximum limit of 10MB",
                org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw BatchJobException.invalidFileType();
        }

        String contentType = file.getContentType();
        if (contentType != null) {
            boolean isValidContentType = false;
            for (String allowedType : ALLOWED_CONTENT_TYPES) {
                if (contentType.contains(allowedType)) {
                    isValidContentType = true;
                    break;
                }
            }
            if (!isValidContentType) {
                log.warn("Unexpected content type: {}, but continuing with CSV processing", contentType);
            }
        }

        log.debug("File validation passed for: {}, size: {} bytes", filename, file.getSize());
    }
} 