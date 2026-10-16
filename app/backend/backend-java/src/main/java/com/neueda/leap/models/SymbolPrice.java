package com.neueda.leap.models;

import java.time.ZonedDateTime;
import java.math.BigDecimal;
import java.util.Objects;

import com.neueda.leap.validators.SymbolPriceValidator;


public class SymbolPrice {
    private final String symbol;
    private final BigDecimal price;
    private final ZonedDateTime timestamp;

    public SymbolPrice(String symbol, BigDecimal price){
        this(symbol, price, new SymbolPriceValidator());
    }

    public SymbolPrice(String symbol, BigDecimal price, SymbolPriceValidator symbolPriceValidator){
        symbolPriceValidator.validateSymbol(symbol);
        symbolPriceValidator.validatePrice(price);
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

    @Override 
    public String toString(){
        return String.format("SymbolPrice [symbol=%s, price=%s, timestamp=%s]", this.symbol, this.price, this.timestamp);
    }

    @Override 
    public boolean equals(Object obj){
        if(this == obj){
            return true;
        } else if (obj == null || getClass() != obj.getClass()){
            return false;
        }
        SymbolPrice other = (SymbolPrice) obj;
        return this.symbol.equals(other.symbol) 
            && this.price.equals(other.price) 
            && this.timestamp.equals(other.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, price, timestamp);
    }

}
