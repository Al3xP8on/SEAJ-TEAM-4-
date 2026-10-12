package com.neueda.leap.dtos;

import com.neueda.leap.models.PriceHistory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fields a client may set when saving a price; the database ID is never taken from the request */
public record PriceHistoryRequest(
    @NotBlank(message = "Symbol must not be blank") String symbol,
    @NotNull(message = "Price date is required") LocalDate priceDate,
    @NotNull(message = "Open price is required") BigDecimal open,
    @NotNull(message = "High price is required") BigDecimal high,
    @NotNull(message = "Low price is required") BigDecimal low,
    @NotNull(message = "Close price is required") BigDecimal close,
    @PositiveOrZero(message = "Volume must be zero or positive") long volume
) {
    public PriceHistory toEntity() {
        return new PriceHistory(symbol, priceDate, open, high, low, close, volume);
    }
}
