package com.wordflip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Aligns with openapi.yaml components/schemas/ErrorResponse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        Map<String, Object> details
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, Instant.now(), path, null);
    }

    public static ErrorResponse of(String code, String message, String path, Map<String, Object> details) {
        return new ErrorResponse(code, message, Instant.now(), path, details);
    }
}
