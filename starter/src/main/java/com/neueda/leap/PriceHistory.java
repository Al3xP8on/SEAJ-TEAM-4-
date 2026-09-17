package com.neueda.leap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import com.neueda.leap.validators.PriceHistoryValidator;

public class PriceHistory {
    
    private final String symbol;
    private final LocalDate priceDate;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final long volume;
    private final PriceHistoryValidator validator;

    public PriceHistory(String symbol, LocalDate priceDate, BigDecimal open, 
                       BigDecimal high, BigDecimal low, BigDecimal close, long volume,
                       PriceHistoryValidator validator) {
        this.validator = Objects.requireNonNull(validator, "Validator cannot be null");
        this.symbol = this.validator.validateSymbol(symbol);
        this.priceDate = this.validator.validateDate(priceDate);
        this.open = this.validator.validatePrice(open, "Open");
        this.high = this.validator.validatePrice(high, "High");
        this.low = this.validator.validatePrice(low, "Low");
        this.close = this.validator.validatePrice(close, "Close");
        this.volume = this.validator.validateVolume(volume);
        
        this.validator.validateOHLCRelationships(this.open, this.high, this.low, this.close);
    }

    public PriceHistory(String symbol, LocalDate priceDate, BigDecimal open, 
                       BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
        this(symbol, priceDate, open, high, low, close, volume, new PriceHistoryValidator());
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

    public PriceHistoryValidator getValidator() {
        return validator;
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
