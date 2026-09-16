package com.neueda.leap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class PriceHistory {
    
    private final String symbol;
    private final LocalDate priceDate;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final long volume;

    public PriceHistory(String symbol, LocalDate priceDate, BigDecimal open, 
                       BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
        this.symbol = validateSymbol(symbol);
        this.priceDate = validateDate(priceDate);
        this.open = validatePrice(open, "Open");
        this.high = validatePrice(high, "High");
        this.low = validatePrice(low, "Low");
        this.close = validatePrice(close, "Close");
        this.volume = validateVolume(volume);
        
        validateOHLCRelationships(this.open, this.high, this.low, this.close);
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }

    public BigDecimal getPriceRange() {
        return high.subtract(low);
    }

    public BigDecimal getDailyChange() {
        return close.subtract(open);
    }

    public BigDecimal getDailyChangePercent() {
        if (open.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getDailyChange()
            .multiply(new BigDecimal("100"))
            .divide(open, 2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isBullish() {
        return close.compareTo(open) > 0;
    }

    public boolean isBearish() {
        return close.compareTo(open) < 0;
    }

    public boolean isNeutral() {
        return close.compareTo(open) == 0;
    }

    private static String validateSymbol(String symbol) {
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

    private static LocalDate validateDate(LocalDate priceDate) {
        Objects.requireNonNull(priceDate, "Price date cannot be null");
        if (priceDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Price date cannot be in the future");
        }
        return priceDate;
    }

    private static BigDecimal validatePrice(BigDecimal price, String priceName) {
        Objects.requireNonNull(price, priceName + " price cannot be null");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(priceName + " price cannot be negative");
        }
        return price;
    }

    private static long validateVolume(long volume) {
        if (volume < 0) {
            throw new IllegalArgumentException("Volume cannot be negative");
        }
        return volume;
    }

    private static void validateOHLCRelationships(BigDecimal open, BigDecimal high, 
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PriceHistory that = (PriceHistory) o;
        return symbol.equals(that.symbol) && priceDate.equals(that.priceDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, priceDate);
    }

    @Override
    public String toString() {
        return "PriceHistory{" +
                "symbol='" + symbol + '\'' +
                ", priceDate=" + priceDate +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                ", dailyChange=" + getDailyChange() + "%" +
                '}';
    }
}
