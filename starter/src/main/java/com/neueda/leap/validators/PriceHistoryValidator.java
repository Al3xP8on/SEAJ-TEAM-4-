package com.neueda.leap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class PriceHistoryValidator {

    public String validateSymbol(String symbol) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        String trimmed = symbol.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be empty");
        }
        if (trimmed.length() > 20) {
            throw new IllegalArgumentException("Symbol cannot exceed 20 characters");
        }
        return trimmed.toUpperCase();
    }

    public LocalDate validateDate(LocalDate priceDate) {
        Objects.requireNonNull(priceDate, "Price date cannot be null");
        if (priceDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Price date cannot be in the future");
        }
        return priceDate;
    }

    public BigDecimal validatePrice(BigDecimal price, String priceName) {
        Objects.requireNonNull(price, priceName + " price cannot be null");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(priceName + " price cannot be negative");
        }
        return price;
    }

    public long validateVolume(long volume) {
        if (volume < 0) {
            throw new IllegalArgumentException("Volume cannot be negative");
        }
        return volume;
    }

    public void validateOHLCRelationships(BigDecimal open, BigDecimal high, 
                                         BigDecimal low, BigDecimal close) {
        if (high.compareTo(low) < 0) {
            throw new IllegalArgumentException("High price must be >= Low price");
        }
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0) {
            throw new IllegalArgumentException("High price must be >= Open and Close prices");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("Low price must be <= Open and Close prices");
        }
    }
}
