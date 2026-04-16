package com.ramesh.user_service.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Unified error structure for the entire platform.
 */
public record ErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp,
        Map<String, String> errors
) {
    public ErrorResponse(int status, String message) {
        this(status, message, LocalDateTime.now(), null);
    }
}