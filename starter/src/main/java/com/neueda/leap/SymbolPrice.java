package com.neueda.leap;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

public class SymbolPrice {
    private String symbol;
    private BigDecimal price;
    private ZonedDateTime timestamp;

    public SymbolPrice(String symbol, BigDecimal price, ZonedDateTime timestamp){
        this.symbol = symbol;
        this.price = price;
        this.timestamp = timestamp;
    }

    public String getSymbol(){
        return this.symbol;
    }

    public BigDecimal getPrice(){
        return this.price;
    }

    public ZonedDateTime getTimestamp(){
        return this.timestamp;
    }
}
