package com.neueda.leap.models;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

public class SymbolPrice {
    private String symbol;
    private BigDecimal price;
    private ZonedDateTime timestamp;

    public SymbolPrice(String symbol, BigDecimal price){
        this.symbol = symbol;
        this.price = price;
        this.timestamp = ZonedDateTime.now();
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
