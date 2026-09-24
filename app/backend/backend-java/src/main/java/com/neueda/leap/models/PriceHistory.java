package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import com.neueda.leap.validators.PriceHistoryValidator;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "price_history")
public class PriceHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    private LocalDate priceDate;
    
    @Column(nullable = false)
    private BigDecimal open;
    
    @Column(nullable = false)
    private BigDecimal high;
    
    @Column(nullable = false)
    private BigDecimal low;
    
    @Column(nullable = false)
    private BigDecimal close;
    
    @Column(nullable = false)
    private long volume;
    
    @Transient
    @JsonIgnore
    private PriceHistoryValidator validator;
    
    // Default constructor for JPA
    public PriceHistory() {
        this.validator = new PriceHistoryValidator();
    }

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = validator.validateSymbol(symbol);
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = validator.validateDate(priceDate);
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = validator.validatePrice(open, "Open");
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = validator.validatePrice(high, "High");
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = validator.validatePrice(low, "Low");
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = validator.validatePrice(close, "Close");
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = validator.validateVolume(volume);
    }

    public PriceHistoryValidator getValidator() {
        return validator;
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
