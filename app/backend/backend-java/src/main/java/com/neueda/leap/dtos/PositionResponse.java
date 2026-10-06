package com.neueda.leap.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.neueda.leap.enums.PositionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record PositionResponse(
    @Schema(description="Position ID", example = "1")
    Long positionId,

    @Schema(description="Account ID", example = "1")
    Long accountId,

    @Schema(description="Trading symbol", example="AAPL")
    String symbol,

    @Schema(description="Number of shares in position", example="100")
    int quantity,

    @Schema(description="Average entry price per share", example="150.00")
    BigDecimal averageCost,

    @Schema(description= "Current position status", example="OPEN")
    PositionStatus status,

    @Schema(description="Time the position was opened")
    LocalDateTime openedAt,

    @Schema(description="Time the position was closed")
    LocalDateTime closedAt,

    @Schema(description="Realised profit or loss", example="500.00")
    BigDecimal realisedPnL
) {}