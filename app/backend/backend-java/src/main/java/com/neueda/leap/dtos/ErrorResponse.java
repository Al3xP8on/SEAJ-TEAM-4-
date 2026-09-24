package com.neueda.leap.dtos;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
    @Schema(description="HTTP status code", example="404")
    @NotNull(message="HTTP status code is required.")
    int status,

    @Schema(description="Detailed error message", example="Resource not found")
    @NotBlank(message="Detailed error message.")
    String message,

    @Schema(description="Timestamp when error occurred", example="2026-09-22T14:30:00Z")
    @NotNull(message="Timestamp when error occurred")
    LocalDateTime timestamp,

    @Schema(description="The API path that was called", example="/api/orders/create")
    @NotBlank(message="API path is required.")
    String path
) {
    
    public ErrorResponse(int status, String message, LocalDateTime timestamp, String path){
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
        this.path = path;
    }
}
