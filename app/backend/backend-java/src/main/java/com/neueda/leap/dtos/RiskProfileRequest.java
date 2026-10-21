package com.neueda.leap.dtos;

import com.neueda.leap.enums.RiskProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RiskProfileRequest(
        @Schema(description = "CONSERVATIVE, BALANCED or AGGRESSIVE", example = "CONSERVATIVE") @NotNull RiskProfile riskProfile
) { }
