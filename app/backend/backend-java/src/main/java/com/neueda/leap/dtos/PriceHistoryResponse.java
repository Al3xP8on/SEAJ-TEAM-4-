package com.neueda.leap.dtos;

import com.neueda.leap.models.PriceHistory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceHistoryResponse(
    String symbol,
    LocalDate priceDate,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    BigDecimal priceRange,
    BigDecimal dailyChange,
    BigDecimal dailyChangePercent
) {
    public static PriceHistoryResponse from(PriceHistory price) {
        return new PriceHistoryResponse(
            price.getSymbol(),
            price.getPriceDate(),
            price.getOpen(),
            price.getHigh(),
            price.getLow(),
            price.getClose(),
            price.getVolume(),
            price.getPriceRange(),
            price.getDailyChange(),
            price.getDailyChangePercent()
        );
    }
}
