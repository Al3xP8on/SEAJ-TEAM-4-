package com.neueda.leap.dtos;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
    @Schema(description="Error code", example="NOT_FOUND")
    @NotBlank(message="Error type is required.")
    String error,

    @Schema(description="Detailed error message", example="Resource not found")
    @NotBlank(message="Detailed error message.")
    String message,

    @Schema(description="Timestamp when error occurred", example="2026-09-22T14:30:00Z")
    @NotNull(message="Timestamp when error occurred")
    LocalDateTime timestamp,

    @Schema(description="HTTP status code", example="404")
    @NotNull(message="HTTP status code is required.")
    int status
) {
    
    public ErrorResponse(String error, String message, LocalDateTime timestamp, int status){
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
        this.status = status;
    }
}
