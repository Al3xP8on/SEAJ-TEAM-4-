package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public record Position (
    @Schema(description="Unique identifier for the position", example="1")
    @Positive(message="PositionId must be a positive number.")
    long positionId,

    @Schema(description="The account that owns this position", example="1")
    @Positive(message="AccountID must be a positive number.")
    long accountId,

    @Schema(description="Trading symbol (e.g., AAPL, MSFT)", example="AAPL")
    @NotNull(message="Symbol must be required.")
    @NotBlank(message="Symbol must not be blank.")
    String symbol,

    @Schema(description="Number of shares in the position", example="100")
    @Positive(message="Quantity must be a positive number.")
    int quantity,

    @Schema(description="Average entry price per share", example="150.00")
    @NotNull(message="Entry price must not be null.")
    BigDecimal entryPrice,

    @Schema(description="Current market price per share (real-time or last known)", example="155.50")
    @NotNull(message="Current price must not be null.")
    BigDecimal currentPrice,

    @Schema(description="Unrealized profit/loss ((currentPrice - entryPrice) * quantity)", example="550.00")
    @NotNull(message="Unrealized PnL must not be null.")
    BigDecimal unrealizedPnL,

    @Schema(description="Unrealized P&L as percentage", example="3.67")
    @NotNull(message="Unrealized PnL percent value must not be null.")
    BigDecimal unrealizedPnLPercent,

    @Schema(description="Current market value of position (currentPrice * quantity)", example="15550.00")
    @NotNull(message="Position value must not be null.")
    BigDecimal positionValue,

    @Schema(description="Current status of the position")
    @NotNull(message="Status must not be null.")
    PositionStatus status,

    @Schema(description="Timestamp when position was opened (first buy order filled)", example="2026-09-20T10:30:00Z")
    @NotNull(message="Opened at must not be null.")
    LocalDateTime openedAt,

    @Schema(description="Timestamp when position was fully closed (null if still open)")
    Optional<LocalDateTime> closedAt,

    @Schema(description="Realized profit/loss from closed portions (null if fully open)")
    Optional<BigDecimal> realizedPnL
){

    public Position(
        long positionId, long accountId, String symbol, int quantity, BigDecimal entryPrice, 
        BigDecimal currentPrice, BigDecimal unrealizedPnL, BigDecimal unrealizedPnLPercent, BigDecimal positionValue, 
        PositionStatus status, LocalDateTime openedAt, Optional<LocalDateTime> closedAt, Optional<BigDecimal> realizedPnL
    ){
        this.positionId = positionId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.currentPrice = currentPrice;
        this.unrealizedPnL = unrealizedPnL;
        this.unrealizedPnLPercent = unrealizedPnLPercent;
        this.positionValue = positionValue;
        this.status = status;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.realizedPnL = realizedPnL;
    }
}
