package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestrictedInstrumentRequest(
        @Schema(example = "META") @NotBlank @Size(max = 20) String symbol,
        @Schema(description = "Shown to customers who try to buy it", example = "pending corporate action review")
        @NotBlank @Size(max = 255) String reason
) { }
