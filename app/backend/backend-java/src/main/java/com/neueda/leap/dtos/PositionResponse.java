package com.neueda.leap.dtos;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

public record PositionResponse(
    @Schema(description="Account ID")
    @NotBlank(message="Account ID must not be blank")
    String accountId,

    @Schema(description="Trading symbol", example="AAPL")
    @NotBlank(message="Symbol must not be blank")
    String symbol,

    @Schema(description="Number of shares in position", example="100")
    @Positive(message="Quantity must be positive")
    int quantity,

    @Schema(description="Average entry price per share", example="150.00")
    @NotNull(message="Average cost must not be null")
    BigDecimal averageCost
) {}