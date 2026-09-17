package com.neueda.leap.dtos;
import java.math.BigDecimal; 

public class PlaceOrderRequest {
    
    private String accountId;
    private String symbol;
    private int quantity;
    private BigDecimal price;

    public PlaceOrderRequest(String accountId, String symbol, int quantity, BigDecimal price) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
    }

    public String getAccountId(){
        return this.accountId;
    }

    public String getSymbol(){
        return this.symbol;
    }

    public int getQuantity(){
        return this.quantity;
    }

    public BigDecimal getPrice(){
        return this.price;
    }
}
